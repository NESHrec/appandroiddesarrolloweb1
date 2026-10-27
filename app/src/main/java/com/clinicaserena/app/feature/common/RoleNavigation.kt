package com.clinicaserena.app.feature.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clinicaserena.app.core.ui.components.LargeTitleBar
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors

/**
 * Destino de la barra inferior: ruta tipada, etiqueta visible e icono. [iconRes] es el ícono relleno de
 * la pestaña activa; [inactiveIconRes], el delineado de las demás (el estado no depende solo del color).
 */
data class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val inactiveIconRes: Int = iconRes,
)

/**
 * Barra inferior + NavHost de un rol. Cada rol tiene su propio grafo: una ruta de paciente no existe
 * en el grafo del médico y viceversa, así que no se puede abrir la sección del otro rol.
 */
@Composable
fun RoleNavScaffold(
    destinations: List<TopLevelDestination>,
    startDestination: Any,
    navController: NavHostController = rememberNavController(),
    topBarActions: @Composable RowScope.(NavHostController) -> Unit = {},
    builder: NavGraphBuilder.() -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTopLevel = destinations.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
    }

    Scaffold(
        // Transparente: se ve el fondo con manchas de la app (SerenaBackground).
        containerColor = Color.Transparent,
        topBar = {
            // Las pantallas secundarias (perfil, detalle) traen su propia barra con Atrás.
            if (currentTopLevel != null) {
                LargeTitleBar(
                    title = stringResource(currentTopLevel.labelRes),
                    actions = { topBarActions(navController) },
                )
            }
        },
        bottomBar = {
            FloatingTabBar(destinations, currentDestination) { destination ->
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            // consumeWindowInsets: las barras propias de pantallas secundarias no repiten el margen superior.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            builder = builder,
        )
    }
}

/**
 * Barra de pestañas flotante (sin desenfoque: no depende de API 31+): pastilla oscura en día y
 * #1A2234 con borde blanco al 8 % en noche. La activa lleva el degradado, ícono relleno y texto blanco;
 * las demás, ícono delineado y su etiqueta visible. Para accesibilidad cada una es una pestaña con su
 * estado `selected`. Con letra grande la etiqueta se recorta en una línea; TalkBack lee el texto completo.
 */
@Composable
private fun FloatingTabBar(
    destinations: List<TopLevelDestination>,
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = LocalSerenaColors.current
    val barShape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(16.dp, barShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)),
        shape = barShape,
        color = colors.tabBar,
        border = BorderStroke(1.dp, colors.tabBarBorder),
    ) {
        Row(
            modifier = Modifier.padding(6.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            destinations.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                val itemShape = RoundedCornerShape(22.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(itemShape)
                        .background(if (selected) Brush.horizontalGradient(colors.gradient) else SolidColor(Color.Transparent))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painterResource(if (selected) destination.iconRes else destination.inactiveIconRes),
                        contentDescription = null,
                        tint = if (selected) colors.onGradient else colors.tabIconInactive,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) colors.onGradient else colors.tabLabelInactive,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
