package com.carlog.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.CarRepository
import com.carlog.domain.model.Car
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CarListState(
    val cars: List<Car> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CarListViewModel @Inject constructor(
    private val carRepository: CarRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(CarListState())
    val state: StateFlow<CarListState> = _state.asStateFlow()
    
    init {
        loadCars()
    }
    
    private fun loadCars() {
        viewModelScope.launch {
            try {
                carRepository.getAllCars().collect { cars ->
                    _state.value = CarListState(
                        cars = cars,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.value = CarListState(
                    cars = emptyList(),
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun deleteCar(car: Car) {
        viewModelScope.launch {
            try {
                carRepository.deleteCar(car)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
}
