package com.iwatchme.jetpackstarter.emailbox

sealed class InboxEvent  {

    object RefreshContent: InboxEvent()

    data class DeleteContent(val id: String): InboxEvent()

}
