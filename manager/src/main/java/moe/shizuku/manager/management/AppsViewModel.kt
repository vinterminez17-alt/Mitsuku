package moe.shizuku.manager.management

import android.content.Context
import android.content.pm.PackageInfo
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.utils.Logger.LOGGER
import rikka.lifecycle.Resource
import rikka.lifecycle.activityViewModels
import rikka.lifecycle.viewModels

@MainThread
fun ComponentActivity.appsViewModel() = viewModels { AppsViewModel(this) }

@MainThread
fun Fragment.appsViewModel() = activityViewModels { AppsViewModel(requireContext()) }

class AppsViewModel(context: Context) : ViewModel() {

    private val _packages = MutableLiveData<Resource<List<PackageInfo>>>()
    val packages = _packages as LiveData<Resource<List<PackageInfo>>>

    private val _grantedCount = MutableLiveData<Resource<Int>>()
    val grantedCount = _grantedCount as LiveData<Resource<Int>>

    /** Allows every app in the list that is not allowed yet, then refreshes the list. */
    fun grantAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                for (pi in AuthorizationManager.getPackages()) {
                    val uid = pi.applicationInfo?.uid ?: continue
                    try {
                        if (!AuthorizationManager.granted(pi.packageName, uid)) {
                            AuthorizationManager.grant(pi.packageName, uid)
                        }
                    } catch (e: Throwable) {
                        // One app failing must not stop the others.
                        LOGGER.w(e, "grantAll: ${pi.packageName}")
                    }
                }
            } catch (e: Throwable) {
                LOGGER.w(e, "grantAll")
            }
            load()
        }
    }

    fun load(onlyCount: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list: MutableList<PackageInfo> = ArrayList()
                var count = 0
                for (pi in AuthorizationManager.getPackages()) {
                    list.add(pi)
                    if (AuthorizationManager.granted(pi.packageName, pi.applicationInfo!!.uid)) count++
                }
                if (!onlyCount) _packages.postValue(Resource.success(list))
                _grantedCount.postValue(Resource.success(count))
            } catch (e: CancellationException) {

            } catch (e: Throwable) {
                _packages.postValue(Resource.error(e, null))
                _grantedCount.postValue(Resource.error(e, 0))
            }
        }
    }
}
