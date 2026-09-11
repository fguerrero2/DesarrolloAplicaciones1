# DesarrolloAplicaciones1
Materia desarrollo de aplicaciones 1

> Repositorio de trabajo, documentación y trazabilidad de los TPO de la materia
> **Desarrollo de aplicaciones 1* — UADE — **2C 2026** — 


**Incluye docs y código fuente para cada TPO.** 


## TPO2_UnitreeG1

TPO obligatorio en donde utilicemos el robot de UADE Unitree G1 y realice tareas definidas. Tendremos que desarrollar y probar mediante el gemelo digital que se disponibiliza para dicho robot. 

La idea es separar el proyecto en dos partes: 
* Un backend desarrollado en VsCode para el control y/o simulación del Unitree G1 en una PC con Ubuntu + SDK2/MuJoCo
* Una app desarrollada en Android Studio que actúe como interfaz de mando y visualización del área. 

No tendremos todo junto en una app para Android, ya que esto sería muy pesado y dificil de implementar.

El robot G1 dispone de soporte en Unitree SDK2, incluyendo una API de locomoción de alto nivel para acciones como ponerse de pie, detenerse, caminar con velocidades vx, vy y rotación vyaw, y ejecutar acciones como saludar con la mano. El repositorio oficial incluye específicamente ejemplos para G1


### Definiciones generales

Se define un area con ancho, largo y un punto de partida, luego se debe mostrar un mapa con la ubicacion del rotot y se presenta una opción que permita moverlo hacia otra posición.  

Por ejemplo, se define un area y punto de partida: 
- Ancho: 6 metros
- Largo: 8 metros
- Punto de partida: (0,0)
- Movimiento X = 2 mt , Y = 5 mt

```mermaid
flowchart TD
    A[Android app (Kotlin + Compose)] -- REST --> [Backend (Unitree SDK2/Python)] 
    B[Backend (Unitree SDK2/Python)] -- DDS --> C[Gemelo digital (MuJoCo G1 3D)]
```


### Link a figma para diseño de los TPO


Link para TPO2_UnitreeG1


### Link a Trello para el seguimiento de las tareas requeridas

🗂️ **Tablero TPO2_UnitreeG1:** 




