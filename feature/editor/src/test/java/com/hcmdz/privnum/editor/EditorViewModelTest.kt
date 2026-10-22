package com.hcmdz.privnum.editor

import android.content.Context
import android.telephony.TelephonyManager
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.ContactPhotoStore
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.Countries
import com.hcmdz.privnum.data.PhoneNumberRef
import com.hcmdz.privnum.data.SaveResult
import com.hcmdz.privnum.data.SettingsStore
import com.hcmdz.privnum.ui.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val tel = mockk<TelephonyManager>()
    private val context = mockk<Context>()
    private val repository = mockk<ContactRepository>()
    private val settings = mockk<SettingsStore>()
    private val photos = mockk<ContactPhotoStore>()

    private lateinit var vm: EditorViewModel
    private val dz = Countries.getByCode("DZ")!!
    private val fr = Countries.getByCode("FR")!!

    private fun row(number: String, primary: Boolean = false) = NumberRow(number, fr, primary)

    private fun stored(id: Long, name: String) = Contact(
        id = id,
        name = name,
        numbers = listOf(PhoneNumberRef("33612345678", "612345678", "FR", primary = true))
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { tel.simCountryIso } returns "DZ"
        every { context.getSystemService(any<String>()) } returns tel
        every { settings.recentCountries } returns flowOf(emptyList())
        every { settings.defaultRegion } returns flowOf<String?>(null)
        coEvery { settings.pushRecentCountry(any()) } returns Unit
        coEvery { repository.getById(any()) } returns null
        coEvery { repository.add(any()) } returns SaveResult.Saved
        coEvery { repository.update(any(), any()) } returns SaveResult.Saved
        coEvery { photos.deletePhoto(any()) } returns Unit
        vm = EditorViewModel(repository, settings, photos, context)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `entering a new contact drops the draft a shared ViewModelStoreOwner leaked`() {
        vm.update { it.copy(name = "Draft") }
        vm.enter(null)

        assertEquals("", vm.state.value.name)
        assertEquals(listOf(NumberRow(country = dz)), vm.state.value.numbers)
        assertFalse(vm.isDirty())
    }

    @Test
    fun `entering another contact loads it instead of keeping the first one`() {
        coEvery { repository.getById(1) } returns stored(1, "First")
        coEvery { repository.getById(2) } returns stored(2, "Second")

        vm.enter(1)
        assertEquals("First", vm.state.value.name)

        vm.enter(2)
        assertEquals("Second", vm.state.value.name)
    }

    @Test
    fun `a contact deleted under the editor is flagged not found`() {
        coEvery { repository.getById(7) } returns null

        vm.enter(7)

        assertTrue(vm.state.value.notFound)
    }

    @Test
    fun `dirty tracking follows edits of a loaded contact`() {
        coEvery { repository.getById(4) } returns stored(4, "Ann")

        vm.enter(4)
        assertFalse(vm.isDirty())

        vm.update { it.copy(name = "Other") }
        assertTrue(vm.isDirty())
    }

    @Test
    fun `editing a row clears the error of that row only`() {
        vm.update {
            it.copy(name = "Ann", numbers = listOf(row("612345678"), row("6")))
        }
        vm.save()
        assertEquals(1, vm.state.value.numberErrorRow)

        vm.updateRow(0) { it.copy(nationalNumber = "612345679") }
        assertNotNull(vm.state.value.numberError)

        vm.updateRow(1) { it.copy(nationalNumber = "623456789") }
        assertNull(vm.state.value.numberError)
        assertEquals(-1, vm.state.value.numberErrorRow)
    }

    @Test
    fun `removing rows keeps one and hands the primary flag over`() {
        vm.update {
            it.copy(numbers = listOf(row("612345678", primary = true), row("623456789")))
        }

        vm.removeNumberRow(0)

        val numbers = vm.state.value.numbers
        assertEquals(1, numbers.size)
        assertTrue(numbers[0].primary)

        vm.removeNumberRow(0)
        assertEquals(1, vm.state.value.numbers.size)
    }

    @Test
    fun `setPrimaryRow leaves a single primary number`() {
        vm.update {
            it.copy(numbers = listOf(row("612345678", primary = true), row("623456789")))
        }

        vm.setPrimaryRow(1)

        assertFalse(vm.state.value.numbers[0].primary)
        assertTrue(vm.state.value.numbers[1].primary)
    }

    @Test
    fun `picking a country records it as recent`() {
        vm.setCountry(fr)

        assertEquals("FR", vm.state.value.numbers[0].country?.code)
        coVerify { settings.pushRecentCountry("FR") }
    }

    @Test
    fun `save refuses a one character name before reaching the repository`() {
        vm.update { it.copy(name = "A", numbers = listOf(row("612345678"))) }
        var done: Boolean? = null

        vm.save { done = it }

        assertNull(done)
        assertEquals(UiText.Resource(R.string.editor_error_name_too_short), vm.state.value.nameError)
        coVerify(exactly = 0) { repository.add(any()) }
    }

    @Test
    fun `save flags the row that cannot be parsed`() {
        vm.update { it.copy(name = "Ann", numbers = listOf(row("612345678"), row("6"))) }

        vm.save()

        assertEquals(UiText.Resource(R.string.editor_error_too_short), vm.state.value.numberError)
        assertEquals(1, vm.state.value.numberErrorRow)
        coVerify(exactly = 0) { repository.add(any()) }
    }

    @Test
    fun `save stores the trimmed contact and clears the dirty flag`() {
        vm.update {
            it.copy(name = "  Ann  ", nickname = " bee ", numbers = listOf(row("612345678", primary = true)))
        }
        var done = false

        vm.save { done = it }

        assertTrue(done)
        assertTrue(vm.state.value.saved)
        assertFalse(vm.isDirty())
        val saved = slot<Contact>()
        coVerify { repository.add(capture(saved)) }
        assertEquals("Ann", saved.captured.name)
        assertEquals("bee", saved.captured.nickname)
        assertEquals(1, saved.captured.numbers.size)
        assertEquals("612345678", saved.captured.numbers[0].national)
        assertEquals("FR", saved.captured.numbers[0].country)
        assertTrue(saved.captured.numbers[0].primary)
    }

    @Test
    fun `saving a loaded contact updates it in place`() {
        coEvery { repository.getById(3) } returns stored(3, "Ann")
        vm.enter(3)
        vm.update { it.copy(notes = " call back ") }

        vm.save()

        val id = slot<Long>()
        val saved = slot<Contact>()
        coVerify { repository.update(capture(id), capture(saved)) }
        coVerify(exactly = 0) { repository.add(any()) }
        assertEquals(3L, id.captured)
        assertEquals("call back", saved.captured.notes)
    }

    @Test
    fun `a duplicate number reports its owner and keeps the draft`() {
        coEvery { repository.add(any()) } returns SaveResult.DuplicateNumber("Bob")
        vm.update { it.copy(name = "Ann", numbers = listOf(row("612345678"))) }
        var done: Boolean? = null

        vm.save { done = it }

        assertEquals(false, done)
        assertEquals(
            UiText.Resource(R.string.editor_error_duplicate_number, listOf("Bob")),
            vm.state.value.message
        )
        assertFalse(vm.state.value.saved)
        assertTrue(vm.isDirty())
    }

    @Test
    fun `a duplicate number without an owner falls back to the generic message`() {
        coEvery { repository.add(any()) } returns SaveResult.DuplicateNumber("")
        vm.update { it.copy(name = "Ann", numbers = listOf(row("612345678"))) }

        vm.save()

        assertEquals(
            UiText.Resource(R.string.editor_error_duplicate_number_unknown),
            vm.state.value.message
        )
    }

    @Test
    fun `saving a contact deleted under the editor flags not found`() {
        coEvery { repository.getById(9) } returns stored(9, "Ann")
        vm.enter(9)
        coEvery { repository.update(9, any()) } returns SaveResult.NotFound
        var done: Boolean? = null

        vm.save { done = it }

        assertEquals(false, done)
        assertTrue(vm.state.value.notFound)
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun `a not found screen never reaches the repository`() {
        coEvery { repository.getById(5) } returns null
        vm.enter(5)
        var done: Boolean? = null

        vm.save { done = it }

        assertEquals(false, done)
        coVerify(exactly = 0) { repository.add(any()) }
        coVerify(exactly = 0) { repository.update(any(), any()) }
    }

    @Test
    fun `removing the photo clears both fields and deletes the stored file`() {
        vm.update { it.copy(photo = "/data/user/0/ann.jpg") }

        vm.removePhoto()

        assertEquals("", vm.state.value.photo)
        assertNull(vm.state.value.pendingPhotoUri)
        coVerify(timeout = 1000) { photos.deletePhoto("/data/user/0/ann.jpg") }
    }
}
