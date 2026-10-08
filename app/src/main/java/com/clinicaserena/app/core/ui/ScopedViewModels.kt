package com.clinicaserena.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Guarda un único [ViewModelStore] activo, identificado por una clave (el acceso, o la persona con
 * sesión). Sobrevive a la rotación porque vive en un ViewModel de la Activity; al cambiar la clave
 * se limpia el anterior, así los datos clínicos y los formularios de otra sesión no quedan en memoria.
 */
class ScopedStoresViewModel : ViewModel() {
    private var currentKey: String? = null
    private var current = ViewModelStore()

    fun storeFor(key: String): ViewModelStore {
        if (key != currentKey) {
            current.clear()
            current = ViewModelStore()
            currentKey = key
        }
        return current
    }

    override fun onCleared() {
        current.clear()
    }
}

/**
 * Todo ViewModel creado dentro de [content] (incluidos los de las rutas de un NavHost) vive en el
 * almacén de [key]. Debe usarse en la raíz, donde el dueño por defecto es la Activity.
 */
@Composable
fun ScopedViewModels(key: String, content: @Composable () -> Unit) {
    val holder: ScopedStoresViewModel = viewModel()
    val owner = remember(key) {
        val store = holder.storeFor(key)
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
