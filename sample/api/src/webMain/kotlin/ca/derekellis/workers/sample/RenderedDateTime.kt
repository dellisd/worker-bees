package ca.derekellis.workers.sample

import kotlinx.serialization.Serializable

@Serializable
data class RenderedDateTime(val date: String, val time: String)
