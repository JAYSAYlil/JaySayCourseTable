import io

# ── 1) 共享元素 CompositionLocal（新文件） ──
locals = '''package com.jaysay.coursetable.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 共享元素转场的两个作用域，由 MainActivity 的 SharedTransitionLayout + AnimatedContent 提供。
 * 采用 CompositionLocal 穿线，避免把作用域参数层层下传到深层卡片。
 * 任一为 null（非转场上下文）时，使用方应退化为普通修饰符。
 */
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

val LocalNavAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }
'''
io.open(r"app/src/main/java/com/jaysay/coursetable/ui/components/SharedScopes.kt", "w", encoding="utf-8", newline="").write(locals)

# ── 2) MainActivity：SharedTransitionLayout 包裹 AnimatedContent 并提供作用域 ──
p = r"app/src/main/java/com/jaysay/coursetable/MainActivity.kt"
s = io.open(p, encoding="utf-8").read()

old = """                    AnimatedContent(
                        targetState = currentScreen(),"""
new = """                    SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    AnimatedContent(
                        targetState = currentScreen(),"""
assert old in s, "animatedcontent open"
s = s.replace(old, new, 1)

old = """                    ) { screen ->
                        screenStateHolder.SaveableStateProvider(screen.name) {"""
new = """                    ) { screen ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        screenStateHolder.SaveableStateProvider(screen.name) {"""
assert old in s, "content lambda"
s = s.replace(old, new, 1)

# 关闭新增的嵌套：找到 SnackbarHost 之后的结构收尾。当前结构：
#   Box { AnimatedContent(...) { screen -> Provider { SaveableStateProvider { when(...) } } } ; SnackbarHost }
# 需要：when 结束后多关一层（CompositionLocalProvider），AnimatedContent 已有收尾不变。
old = """                        }
                        }
                    }
                    SnackbarHost("""
new = """                        }
                        }
                        }
                    }
                    SnackbarHost("""
assert old in s, "close nesting"
s = s.replace(old, new, 1)

# SharedTransitionLayout 的包裹需要在 Box 结束处多关一层 —— 找 Box 尾部
old = """                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {"""
# 在其后不会加层；SharedTransitionLayout 的闭合应加在 Box 的收尾。Box 的收尾与 AnimatedContent 收尾相邻，
# 观察上面 "close nesting" 替换已使 AnimatedContent 闭合平衡；还需在 Box 尾（SnackbarHost 之后）追加 "}"
old = """                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
                    )
                }
            }
        }
    }"""
new = """                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
                    )
                }
                }
            }
        }
    }"""
assert old in s, "box close"
s = s.replace(old, new, 1)

# 导入
for imp in [
    "import androidx.compose.animation.SharedTransitionLayout",
    "import com.jaysay.coursetable.ui.components.LocalNavAnimatedVisibilityScope",
    "import com.jaysay.coursetable.ui.components.LocalSharedTransitionScope",
]:
    if imp not in s:
        s = s.replace("import androidx.compose.animation.AnimatedContent", imp + "\nimport androidx.compose.animation.AnimatedContent", 1)
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("main wired")
