package com.jaysay.coursetable.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.AgendaCourseInstance
import com.jaysay.coursetable.data.model.AgendaDateGroup
import com.jaysay.coursetable.data.model.AgendaListCalculator
import com.jaysay.coursetable.data.model.AgendaSection
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.ui.theme.pressScale
import com.jaysay.coursetable.util.TimeUtils
import com.jaysay.coursetable.util.rememberToday
import java.time.LocalDate

/**
 * 独立的手机日程列表视图。
 *
 * 传入 [searchQuery] 和 [onSearchQueryChange] 时可作为受控组件接入现有搜索状态；
 * 两者都不传时则自带可恢复的本地搜索框。该屏幕不读写课表、偏好或提醒数据。
 */
@Composable
fun AgendaScreen(
    courses: List<Course>,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
    periodTimes: List<PeriodTime> = AppPreferences.defaultPeriods(),
    semesterStart: String = TimeUtils.currentWeekStartDate(),
    totalWeeks: Int = 20,
    excludedWeeks: List<Int> = emptyList(),
    dateExceptions: List<ScheduleDateException> = emptyList(),
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    today: LocalDate = rememberToday().value
) {
    var localSearchQuery by rememberSaveable { mutableStateOf("") }
    val query = searchQuery ?: localSearchQuery
    val onQueryChange: (String) -> Unit = { value ->
        if (onSearchQueryChange != null) onSearchQueryChange(value) else localSearchQuery = value
    }
    val groups = remember(courses, periodTimes, semesterStart, totalWeeks, excludedWeeks, dateExceptions, query, today) {
        AgendaListCalculator.calculate(
            courses = courses,
            periods = periodTimes,
            semesterStart = semesterStart,
            totalWeeks = totalWeeks,
            fromDate = today,
            excludedWeeks = excludedWeeks.toSet(),
            exceptions = dateExceptions,
            searchQuery = query
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("agenda-search"),
            label = { Text(stringResource(R.string.agenda_search_label)) },
            singleLine = true
        )
        if (groups.isEmpty()) {
            AgendaEmptyState(query = query, onClearSearch = { onQueryChange("") })
        } else {
            AgendaList(
                groups = groups,
                onCourseClick = onCourseClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AgendaEmptyState(query: String, onClearSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("agenda-empty"),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (query.isBlank()) stringResource(R.string.agenda_empty_no_courses) else stringResource(R.string.agenda_empty_no_match, query),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (query.isBlank()) stringResource(R.string.agenda_empty_hint_hidden) else stringResource(R.string.agenda_empty_hint_search),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (query.isNotBlank()) {
            TextButton(onClick = onClearSearch, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.agenda_button_clear_search))
            }
        }
    }
}

@Composable
private fun AgendaList(
    groups: List<AgendaDateGroup>,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupsBySection = remember(groups) { groups.groupBy { it.section } }
    LazyColumn(
        modifier = modifier.testTag("agenda-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AgendaSection.entries.forEach { section ->
            val sectionGroups = groupsBySection[section].orEmpty()
            if (sectionGroups.isNotEmpty()) {
                item(key = "agenda-section-${section.name}") {
                    Text(
                        text = section.label,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(sectionGroups, key = { "agenda-date-${it.date}" }) { group ->
                    AgendaDateCard(group = group, onCourseClick = onCourseClick)
                }
            }
        }
    }
}

@Composable
private fun AgendaDateCard(group: AgendaDateGroup, onCourseClick: (Course) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = AppShapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = stringResource(
                    R.string.agenda_date_header,
                    formatDate(group.date),
                    TimeUtils.getDayName(group.date.dayOfWeek.value),
                    group.week
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        group.courses.forEach { instance ->
            AgendaCourseCard(instance = instance, onClick = { onCourseClick(instance.course) })
        }
    }
}

@Composable
private fun AgendaCourseCard(instance: AgendaCourseInstance, onClick: () -> Unit) {
    val course = instance.course
    val teacherDescription = stringResource(R.string.agenda_a11y_teacher, course.teacher)
    val classroomDescription = stringResource(R.string.agenda_a11y_classroom, course.classroom)
    val doubleTapHint = stringResource(R.string.agenda_a11y_double_tap_detail)
    val details = buildList {
        add(course.courseName)
        add(formatDate(instance.date))
        add(TimeUtils.getDayName(instance.date.dayOfWeek.value))
        add(instance.timeLabel)
        add(instance.periodLabel)
        if (course.teacher.isNotBlank()) add(teacherDescription)
        if (course.classroom.isNotBlank()) add(classroomDescription)
        add(doubleTapHint)
    }.joinToString("，")
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agenda-course-${course.courseId}-${instance.date}")
            .semantics(mergeDescendants = true) { contentDescription = details }
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${instance.timeLabel} · ${instance.periodLabel}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            if (course.teacher.isNotBlank()) {
                Text(
                    text = stringResource(R.string.agenda_label_teacher, course.teacher),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (course.classroom.isNotBlank()) {
                Text(
                    text = stringResource(R.string.agenda_label_classroom, course.classroom),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDate(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"
