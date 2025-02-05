package de.muzzletov.model

data class ContainerProps(val desc: String, val name: String, val file: String, val ports: ArrayList<String>, var checksum: String?)
