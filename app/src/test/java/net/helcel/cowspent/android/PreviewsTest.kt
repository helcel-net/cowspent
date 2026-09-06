package net.helcel.cowspent.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import net.helcel.cowspent.NoInfiniteAnimationsRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every `@Preview` in the app, rendered once.
 *
 * Previews are compiled into the release APK and are the only caller of some composables with
 * awkward argument shapes, so a preview that throws is a real compile-time-clean runtime break that
 * nothing else would catch. This is a smoke test: it asserts the tree composed, not what it looks
 * like — the behaviour of each screen is covered by that screen's own tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2400dp")
class PreviewsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val noInfiniteAnimations = NoInfiniteAnimationsRule()

    private fun renders(preview: @Composable () -> Unit) {
        composeTestRule.setContent { preview() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().assertExists()
    }

    @Test fun testBillItemRow() = renders { net.helcel.cowspent.android.main.BillItemRowPreview() }
    @Test fun testSectionHeader() = renders { net.helcel.cowspent.android.main.SectionHeaderPreview() }
    @Test fun testEmptyProjectsState() = renders { net.helcel.cowspent.android.main.EmptyProjectsStatePreview() }
    @Test fun testEmptyMembersState() = renders { net.helcel.cowspent.android.main.EmptyMembersStatePreview() }
    @Test fun testEmptyBillsState() = renders { net.helcel.cowspent.android.main.EmptyBillsStatePreview() }

    @Test fun testEditBillScreen() = renders { net.helcel.cowspent.android.bill_edit.EditBillScreenPreview() }
    @Test fun testEditBillScreenWeighted() = renders { net.helcel.cowspent.android.bill_edit.EditBillScreenWeightedPreview() }
    @Test fun testEditBillScreenCustom() = renders { net.helcel.cowspent.android.bill_edit.EditBillScreenCustomPreview() }
    @Test fun testEditBillScreenPercent() = renders { net.helcel.cowspent.android.bill_edit.EditBillScreenPercentPreview() }

    @Test fun testUserAvatar() = renders { net.helcel.cowspent.android.helper.UserAvatarPreview() }
    @Test fun testUserAvatarCustomColor() = renders { net.helcel.cowspent.android.helper.UserAvatarCustomColorPreview() }
    @Test fun testUserAvatarDisabled() = renders { net.helcel.cowspent.android.helper.UserAvatarDisabledPreview() }

    @Test fun testProjectSettlementUI() = renders { net.helcel.cowspent.android.project.settle.ProjectSettlementUIPreview() }
    @Test fun testProjectSettlementUIBalanced() = renders { net.helcel.cowspent.android.project.settle.ProjectSettlementUIBalancedPreview() }

    @Test fun testLabelManagementCategories() = renders { net.helcel.cowspent.android.label.LabelManagementCategoriesPreview() }
    @Test fun testLabelManagementPaymentModes() = renders { net.helcel.cowspent.android.label.LabelManagementPaymentModesPreview() }

    @Test fun testDrawerItem() = renders { net.helcel.cowspent.android.drawer.DrawerItemPreview() }
    @Test fun testDrawer() = renders { net.helcel.cowspent.android.drawer.DrawerPreview() }

    @Test fun testCurrencyRow() = renders { net.helcel.cowspent.android.currencies.CurrencyRowPreview() }
    @Test fun testManageCurrenciesScreen() = renders { net.helcel.cowspent.android.currencies.ManageCurrenciesScreenPreview() }

    @Test fun testProjectStatisticsTable() = renders { net.helcel.cowspent.android.statistics.ProjectStatisticsTablePreview() }
    @Test fun testProjectSpendingGraph() = renders { net.helcel.cowspent.android.statistics.ProjectSpendingGraphPreview() }
    @Test fun testProjectSankeyDiagram() = renders { net.helcel.cowspent.android.statistics.ProjectSankeyDiagramPreview() }

    @Test fun testProjectShareDialogContent() = renders { net.helcel.cowspent.android.project.ProjectShareDialogContentPreview() }
    @Test fun testMemberManagementScreen() = renders { net.helcel.cowspent.android.project.member.MemberManagementScreenPreview() }
    @Test fun testMemberEditDialogContent() = renders { net.helcel.cowspent.android.project.member.MemberEditDialogContentPreview() }
    @Test fun testMemberAddDialogContent() = renders { net.helcel.cowspent.android.project.member.MemberAddDialogContentPreview() }
    @Test fun testEditProjectScreen() = renders { net.helcel.cowspent.android.project.edit.EditProjectScreenPreview() }
    @Test fun testNewProjectScreen() = renders { net.helcel.cowspent.android.project.create.NewProjectScreenPreview() }

    @Test fun testColorPicker() = renders { net.helcel.cowspent.android.helper.ColorPickerPreview() }
    @Test fun testLabelBillsScreen() = renders { net.helcel.cowspent.android.bill_label.LabelBillsScreenPreview() }
    @Test fun testAccountScreen() = renders { net.helcel.cowspent.android.account.AccountScreenPreview() }
    @Test fun testAboutScreen() = renders { net.helcel.cowspent.android.about.AboutScreenPreview() }

    @Test fun testSimpleAlertDialog() = renders { net.helcel.cowspent.android.helper.SimpleAlertDialogPreview() }
    @Test fun testConfirmationDialog() = renders { net.helcel.cowspent.android.helper.ConfirmationDialogPreview() }
    @Test fun testListDialogWithIcons() = renders { net.helcel.cowspent.android.helper.ListDialogWithIconsPreview() }

    /** Opens a real database, so it is the one preview that needs a Robolectric context to exist. */
    @Test fun testBillsListScreen() = renders { net.helcel.cowspent.android.main.BillsListScreenPreview() }
}
