/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.utils.ArrayMapAccessor
import org.jetbrains.kotlin.fir.utils.ComponentArrayOwner
import org.jetbrains.kotlin.fir.utils.TypeRegistry
import kotlin.reflect.KClass

@RequiresOptIn
annotation class PluginServicesInitialization

class FirExtensionService(val session: FirSession) : ComponentArrayOwner<FirExtension, List<FirExtension>>(), FirSessionComponent {
    companion object : TypeRegistry<FirExtension, List<FirExtension>>() {
        inline fun <reified P : FirExtension, V : List<P>> registeredExtensions(): ArrayMapAccessor<FirExtension, List<FirExtension>, V> {
            return generateAccessor(P::class)
        }
    }

    override val typeRegistry: TypeRegistry<FirExtension, List<FirExtension>>
        get() = Companion

    var registeredExtensionsSize: Int = 0
        private set

    @PluginServicesInitialization
    fun registerExtensions(extensionClass: KClass<out FirExtension>, extensionFactories: List<FirExtension.Factory<*>>) {
        registeredExtensionsSize += extensionFactories.size
        registerComponent(
            extensionClass,
            extensionFactories.map { it.create(session) }
        )
    }

    @PluginServicesInitialization
    fun getAllExtensions(): List<FirExtension> {
        return arrayMap.flatten()
    }
}

val FirSession.extensionService: FirExtensionService by FirSession.sessionComponentAccessor()

val FirExtensionService.hasExtensions: Boolean
    get() = registeredExtensionsSize > 0
