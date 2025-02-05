package de.muzzletov.model

data class Container(val Id: String, val ImageID: String, val Ports: Array<Port>, val Names: Array<String>, val State: String)
