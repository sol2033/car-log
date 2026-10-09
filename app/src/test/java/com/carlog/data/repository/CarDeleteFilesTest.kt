package com.carlog.data.repository

import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.CarDocumentDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.ExpenseDao
import com.carlog.data.local.dao.FineDao
import com.carlog.data.local.dao.OilTopUpDao
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.dao.RefuelingDao
import com.carlog.domain.model.Car
import com.carlog.domain.model.Fine
import com.carlog.domain.model.FineViolationTypes
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Удаление машины чистит файлы её записей (правило 6): каскад БД удаляет строки,
 * но не фото и PDF в хранилище.
 *
 * Регрессия: вложения штрафов (модуль с 1.3.0) при удалении машины не удалялись
 * и оставались в хранилище — и в каждом бэкапе — навсегда.
 */
class CarDeleteFilesTest {

    private val carDao = mockk<CarDao>(relaxed = true)
    private val fineDao = mockk<FineDao>(relaxed = true)

    private val repository = CarRepository(
        carDao,
        mockk<BreakdownDao>(relaxed = true),
        mockk<RefuelingDao>(relaxed = true),
        mockk<ConsumableDao>(relaxed = true),
        mockk<PartDao>(relaxed = true),
        mockk<AccidentDao>(relaxed = true),
        mockk<ExpenseDao>(relaxed = true),
        mockk<CarDocumentDao>(relaxed = true),
        mockk<OilTopUpDao>(relaxed = true),
        fineDao
    )

    private val car = Car(
        id = 1, brand = "Lada", model = "Vesta", year = null, color = null,
        licensePlate = null, vin = null, engineModel = null, engineVolume = null,
        transmissionType = null, driveType = null, bodyType = null, fuelType = "Бензин",
        hasGasEquipment = false, gasType = null, currentMileage = 100_000,
        purchaseMileage = null, purchaseDate = null, mainPhotoPath = null,
        photosPaths = null, notes = null, createdAt = 0, updatedAt = 0
    )

    @Test
    fun `удаление машины удаляет вложения её штрафов`() = runTest {
        val photo = File.createTempFile("fine_photo", ".jpg")
        val pdf = File.createTempFile("fine_doc", ".pdf")
        every { fineDao.getFinesByCarId(1) } returns flowOf(
            listOf(
                Fine(
                    id = 1, carId = 1, date = 0, amount = 500.0,
                    violationType = FineViolationTypes.SPEEDING,
                    photosPaths = listOf(photo.path, pdf.path)
                )
            )
        )
        assertTrue(photo.exists() && pdf.exists())

        repository.deleteCar(car)

        assertFalse("фото постановления удалено", photo.exists())
        assertFalse("PDF постановления удалён", pdf.exists())
        coVerify { carDao.deleteCar(car) }
    }
}
