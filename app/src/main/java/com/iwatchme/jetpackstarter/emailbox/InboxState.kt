package com.iwatchme.jetpackstarter.emailbox

data class InboxState(
    val  status: InboxStatus = InboxStatus.LOADING,
    val emails : List<Email> = emptyList()
)


enum class InboxStatus {
    LOADING, ERROR, SUCCESS,EMPTY
}


data class Email(
    val id: String,
    val title:String,
    val description: String
)