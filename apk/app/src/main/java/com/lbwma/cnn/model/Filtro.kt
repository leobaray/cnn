package com.lbwma.cnn.model

const val FOTOS_POR_FILTRO = 275

data class Filtro(
    val id: Int,
    val prefix: String,
    val linha1: String,
    val linha2: String
)

val FILTROS = listOf(
    Filtro(1,  "f01", "C · Tampa ou Fita", "Banc. · Novo"),
    Filtro(2,  "f02", "S · Tampa ou Fita", "Banc. · Novo"),
    Filtro(3,  "f03", "C · Pano",  "Banc. · Novo"),
    Filtro(4,  "f04", "S · Pano",  "Banc. · Novo"),
    Filtro(5,  "f05", "C · Tampa ou Fita", "Mesa · Novo"),
    Filtro(6,  "f06", "S · Tampa ou Fita", "Mesa · Novo"),
    Filtro(7,  "f07", "C · Pano",  "Mesa · Novo"),
    Filtro(8,  "f08", "S · Pano",  "Mesa · Novo"),
    Filtro(9,  "f09", "C · Tampa ou Fita", "Banc. · Velho"),
    Filtro(10, "f10", "S · Tampa ou Fita", "Banc. · Velho"),
    Filtro(11, "f11", "C · Pano",  "Banc. · Velho"),
    Filtro(12, "f12", "S · Pano",  "Banc. · Velho"),
    Filtro(13, "f13", "C · Tampa ou Fita", "Mesa · Velho"),
    Filtro(14, "f14", "S · Tampa ou Fita", "Mesa · Velho"),
    Filtro(15, "f15", "C · Pano",  "Mesa · Velho"),
    Filtro(16, "f16", "S · Pano",  "Mesa · Velho"),
)
