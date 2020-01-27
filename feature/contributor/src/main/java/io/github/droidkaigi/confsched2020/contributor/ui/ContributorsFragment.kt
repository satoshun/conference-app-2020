package io.github.droidkaigi.confsched2020.contributor.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.observe
import com.google.android.material.snackbar.Snackbar
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.databinding.ViewHolder
import dagger.Component
import dagger.Module
import dagger.Provides
import io.github.droidkaigi.confsched2020.App
import io.github.droidkaigi.confsched2020.contributor.R
import io.github.droidkaigi.confsched2020.contributor.databinding.FragmentContributorsBinding
import io.github.droidkaigi.confsched2020.contributor.ui.di.ContributorAssistedInjectModule
import io.github.droidkaigi.confsched2020.contributor.ui.item.ContributorItem
import io.github.droidkaigi.confsched2020.contributor.ui.viewmodel.ContributorsViewModel
import io.github.droidkaigi.confsched2020.di.AppComponent
import io.github.droidkaigi.confsched2020.di.PageScope
import io.github.droidkaigi.confsched2020.ext.assistedViewModels
import io.github.droidkaigi.confsched2020.ext.stringRes
import io.github.droidkaigi.confsched2020.model.AppError
import io.github.droidkaigi.confsched2020.model.Contributor
import io.github.droidkaigi.confsched2020.util.ProgressTimeLatch
import javax.inject.Inject
import javax.inject.Provider

class ContributorsFragment : Fragment(R.layout.fragment_contributors) {

    @Inject lateinit var contributorsFactory: Provider<ContributorsViewModel>
    private val contributorsViewModel by assistedViewModels {
        contributorsFactory.get()
    }
    @Inject lateinit var contributorItemFactory: ContributorItem.Factory

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        inject()
        val binding = FragmentContributorsBinding.bind(view)

        val groupAdapter = GroupAdapter<ViewHolder<*>>()
        binding.contributorRecycler.adapter = groupAdapter

        val progressTimeLatch = ProgressTimeLatch { showProgress ->
            binding.progressBar.isVisible = showProgress
        }.apply {
            loading = true
        }

        binding.retryButton.setOnClickListener {
            contributorsViewModel.onRetry()
        }

        contributorsViewModel.uiModel.observe(viewLifecycleOwner) { uiModel ->
            progressTimeLatch.loading = uiModel.isLoading
            groupAdapter.update(uiModel.contributors.toItems())
            binding.retryButton.visibility =
                if (uiModel.error != null && uiModel.contributors.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            uiModel.error?.let {
                showErrorSnackbar(
                    binding.contributor,
                    it,
                    binding.retryButton.visibility != View.VISIBLE
                )
            }
        }
    }

    private fun List<Contributor>.toItems() =
        map {
            contributorItemFactory.create(it)
        }

    private fun showErrorSnackbar(view: View, appError: AppError, showRetryAction: Boolean) {
        Snackbar.make(
            view,
            appError.stringRes(),
            Snackbar.LENGTH_LONG
        ).apply {
            if (showRetryAction) {
                setAction(R.string.retry_label) {
                    contributorsViewModel.onRetry()
                }
            }
        }.show()
    }

    private fun inject() {
        val appComponent = (requireContext().applicationContext as App).appComponent
        val component = DaggerContributorComponent.factory()
            .create(appComponent, ContributorModule(this))
        component.inject(this)
    }
}

@Module
class ContributorModule(private val fragment: ContributorsFragment) {
    @PageScope @Provides
    fun providesLifecycleOwnerLiveData(): LiveData<LifecycleOwner> {
        return fragment.viewLifecycleOwnerLiveData
    }
}

@PageScope
@Component(
    modules = [
        ContributorModule::class,
        ContributorAssistedInjectModule::class
    ],
    dependencies = [AppComponent::class]
)
interface ContributorComponent {
    @Component.Factory
    interface Factory {
        fun create(
            appComponent: AppComponent,
            contributorModule: ContributorModule
        ): ContributorComponent
    }

    fun inject(fragment: ContributorsFragment)
}
