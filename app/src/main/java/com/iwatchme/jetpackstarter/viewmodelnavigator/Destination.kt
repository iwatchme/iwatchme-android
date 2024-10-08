package com.iwatchme.jetpackstarter.viewmodelnavigator

sealed interface Destination {

    data object HomeGraph : Destination
}