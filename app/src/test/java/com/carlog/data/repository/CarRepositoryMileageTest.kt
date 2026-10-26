package com.carlog.data.repository

import com.carlog.data.local.dao.FineDao
import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.CarDocumentDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.ExpenseDao
import com.carlog.data.local.dao.OilTopUpDao
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.dao.RefuelingDao
import com.carlog.domain.model.Car
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Пробег машины — максимум по всем записям (§3.2), доливы масла в нём участвуют.
 * Регрессия: пересчёт после удаления и синхронизация при запуске доливы не видели,
 * и пробег машины мог упасть ниже пробега последнего долива.
 */
class CarRepositoryMileageTest {

    private val carDao = mockk<CarDao>(relaxed = true)
    private val refuelingDao = mockk<RefuelingDao>(relaxed = true)
    private val oilTopUpDao = mockk<OilTopUpDao>(relaxed = true)
    private val partDao = mockk<PartDao>(relaxed = true)

    private val repository = CarRepository(
        carDao,
        mockk<BreakdownDao>(relaxed = true),
        refuelingDao,
        mockk<ConsumableDao>(relaxed = true),
        partDao,
        mockk<AccidentDao>(relaxed = true),
        mockk<ExpenseDao>(relaxed = true),
        mockk<CarDocumentDao>(relaxed = true),
        oilTopUpDao,
        mockk<FineDao>(relaxed = true)
    )

    private val car = Car(
        id = 1, brand = "Lada", model = "Vesta", year = null, color = null,
        licensePlate = null, vin = null, engineModel = null, engineVolume = null,
        transmissionType = null, driveType = null, bodyType = null, fuelType = "Бензин",
        currentMileage = 100_000, purchaseMileage = null, purchaseDate = null,
        mainPhotoPath = null, photosPaths = null, notes = null, createdAt = 0, updatedAt = 0
    )

    @Test
    fun `после удаления пробег не опускается ниже долива`() = runTest {
        coEvery { carDao.getCarByIdOnce(1) } returns car
        coEvery { refuelingDao.getMaxMileage(1) } returns 95_000
        coEvery { oilTopUpDao.getMaxMileage(1) } returns 98_000

        // Удалили заправку на 100 000 — новый максимум 98 000 (долив), а не 95 000
        repository.updateCarMileageAfterDelete(1, 100_000)

        coVerify { carDao.updateMileage(1, 98_000, any()) }
    }

    @Test
    fun `синхронизация при запуске поднимает пробег до долива`() = runTest {
        coEvery { carDao.getAllCarsOnce() } returns listOf(car.copy(currentMileage = 90_000))
        coEvery { oilTopUpDao.getMaxMileage(1) } returns 98_000

        repository.syncAllCarsMileage()

        coVerify { carDao.updateMileage(1, 98_000, any()) }
    }

    /** Регрессия: удаление единственной записи обнуляло пробег машины */
    @Test
    fun `удаление последней записи не обнуляет пробег, а опирается на пробег покупки`() = runTest {
        coEvery { carDao.getCarByIdOnce(1) } returns car.copy(currentMileage = 100_500, purchaseMileage = 80_000)

        repository.updateCarMileageAfterDelete(1, 100_500)

        coVerify { carDao.updateMileage(1, 80_000, any()) }
        coVerify(exactly = 0) { carDao.updateMileage(1, 0, any()) }
    }

    @Test
    fun `без записей и пробега покупки пробег после удаления не трогается`() = runTest {
        coEvery { carDao.getCarByIdOnce(1) } returns car.copy(currentMileage = 100_500)

        repository.updateCarMileageAfterDelete(1, 100_500)

        coVerify(exactly = 0) { carDao.updateMileage(any(), any(), any()) }
    }

    @Test
    fun `пробег поломки запчасти держит пробег машины после удаления`() = runTest {
        coEvery { carDao.getCarByIdOnce(1) } returns car
        coEvery { refuelingDao.getMaxMileage(1) } returns 95_000
        coEvery { partDao.getMaxBreakdownMileage(1) } returns 99_000

        repository.updateCarMileageAfterDelete(1, 100_000)

        coVerify { carDao.updateMileage(1, 99_000, any()) }
    }
}
