package de.muzzletov.model

data class ContainerOut(
    val Hostname: String = "",
    val Domainname: String = "",
    val User: String = "",
    val AttachStdin:Boolean = false,
    val AttachStdout:Boolean = true,
    val AttachStderr:Boolean = true,
    val Tty:Boolean = false,
    val OpenStdin:Boolean = false,
    val StdinOnce:Boolean = false,
    val Image: String = "",
)
