package de.muzzletov.model

data class ContainerModel(val props: ContainerProps, var state: State, var imageId: String? = null, var id: String? = null, var imageName: String = "" )
