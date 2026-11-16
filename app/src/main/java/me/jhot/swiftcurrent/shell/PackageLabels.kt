package me.jhot.swiftcurrent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The package manager's names for [packages], for the ones nothing else can name (a shortcut's app that is
 * gone). The package manager is blocking IPC, so it is read off the main thread, once per set of packages;
 * until it answers, and for a package it does not know, there is simply no entry.
 */
@Composable
internal fun rememberPackageLabels(deps: LauncherDependencies, io: CoroutineDispatcher, packages: List<String>): State<Map<String, String>> =
    produceIo(emptyMap<String, String>(), io, packages.distinct()) {
        packages.distinct().mapNotNull { pkg -> deps.packageLabel(pkg)?.let { pkg to it } }.toMap()
    }
