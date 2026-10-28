package ar.edu.fguerrero.da1.agendasalud.presentation.navegacion

import kotlinx.serialization.Serializable

// Rutas tipadas de Navigation Compose. Las acciones de cada pantalla se resuelven con diálogos y paneles.

@Serializable data object RutaHoy
@Serializable data object RutaAgenda
@Serializable data object RutaPacientes
@Serializable data object RutaCobros
