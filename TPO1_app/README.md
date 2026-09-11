# DesarrolloAplicaciones1
Materia desarrollo de aplicaciones 1 

> Repositorio de trabajo, documentación y trazabilidad de los TPO de la materia
> **Desarrollo de aplicaciones 1* — UADE — **2C 2026** — 


**Incluye docs y código fuente para cada TPO.** 

## TPO1_app

TPO obligatorio con el desarrollo de la solución a un problema y su implementación mediante una app para celular



### Definiciones generales

Los profesionales independientes de psicología, psicopedagogía y fonoaudiología suelen administrar su actividad combinando calendario, WhatsApp, notas, planillas y comprobantes y existe un problema con la gestión de turnos cuando se registra en una agenda en porque es complejo mantener actualizada la agenda, asistencia, reprogramaciones, cancelaciones y dinero pendiente de cobro sin perder tiempo administrativo.


Problemas concretos
•	Agenda fragmentada: dificultad para distinguir rápidamente horarios ocupados, disponibles y bloqueados.
•	Reprogramaciones manuales: se consulta el calendario, se conversa por WhatsApp y luego se debe recordar actualizar el turno.
•	Falta de trazabilidad financiera: sesiones cobradas, medio de cobro, sesiones pendientes y saldo por paciente quedan dispersos entre whatsapp y agenda física.
•	Cancelaciones repetidas: no existe una vista simple del acumulado anual por paciente.
•	Agenda profesional no clínica: vacaciones, cursos y eventos compiten por los mismos horarios que los turnos y no hay forma rápida de ver el calendario con restricciones.
•	Privacidad: los datos del paciente requieren mayor protección que una libreta, chat o planilla sin controles.

#### Modelo inicial “local-first” para usuario profesional de la salud

Adoptamos inicialmente un enfoque  “local-first” para almacenamiento de los datos cifrados en forma local sin implementar un backend para persistir datos en la nube, ademas 
el diseño de la app implica que solamente el profesional de la salud será el usuario el una primera etapa solo utilizando las opciones gratis para registro de agendas, cobros y cancelaciones

 “local-first” segun https://www.inkandswitch.com/essay/local-first/


 #### Modelo para segunda fase con inclusion de pacientes como usuarios
En una segunda etapa se dispondrá de un backend cuando se habilite a los pacientes para interactuar con la misma app para registrar sus datos de prepaga u obra social, ver agendas de citas, solicitar reprogramaciones, solicitar cancelaciones, registrar pago con QR, solicitar emision de factura.
Este modelo implica que se necesita un backend para las interacciones entre pacientes y profesional, ademas e la persistencia en un backend debido a los problemas que puede presentar la conexión directa entre app en el profesional y app en el paciente.



### Link a figma para diseño de los TPO


Link para TPO1_app

https://www.figma.com/design/rgXvOl5ZgXYwsMAkkrWbtS/Wireframe-Problema-TPO-agenda-medica?node-id=1-49&p=f&t=NSO5FcfrnPh9sFEB-0


### Link a Trello para el seguimiento de las tareas requeridas

🗂️ **Tablero TPO1_app:** 




