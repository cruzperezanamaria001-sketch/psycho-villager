# Cambios

## 1.1.0

**Juego de detectives: testigos e inocencia fingida.**

### Sistema de testigos
- Cuando el asesino mata, los aldeanos que lo ven (con linea de vista, hasta 32 bloques) tienen
  un 60 % de probabilidad de convertirse en **testigos**.
- Un testigo cerca (hasta 16 bloques) **reconoce al asesino** y recuerda su nombre. Si esta mas
  lejos, ve el ataque pero **no puede identificarlo**.
- Recuerdan de verdad (se guarda en el propio aldeano, sobrevive a descargar el chunk o cerrar el
  mundo): nombre del asesino, color de su ropa, donde y cuando fue, hacia donde huyo (solo si
  todavia lo ve alejarse) y la victima. Nunca inventan nada: si no vieron nada, responden que no saben.
- Los testigos reaccionan al azar: **se esconden**, **buscan ayuda**, **se quedan paralizados** o
  **huyen**. El asesino prefiere matar a los testigos que lo reconocieron.

### Interrogar aldeanos
- **Agachado + clic derecho** sobre un aldeano abre un menu en el chat con 4 preguntas (se pulsan):
  ¿Viste algo? / ¿Dónde estabas anoche? / ¿Viste a alguien sospechoso? / ¿Quién crees que es el asesino?
- El clic derecho normal sigue abriendo el comercio. Para que el clic derecho siempre interrogue,
  pon `INTERROGATE_REQUIRES_SNEAK = false` en `Config.java`.
- El asesino tambien responde... pero **miente** (coartadas falsas y acusaciones a otros).
- La primera vez que interrogas a alguien recibes el libro **Registro de investigaciones**.

### Registro de investigaciones
- Libro que se actualiza solo cada vez que lo abres: casos (victima, lugar, cuando), testigos
  interrogados, pistas descubiertas y coartadas. Solo apunta lo que tu has descubierto.
- Comando `/psycho book` para conseguir otro.

### Fingir inocencia
- De dia (y de noche antes de empezar a cazar) el asesino es un aldeano normal: sin espada, sin
  particulas, trabaja, camina y duerme.
- Cada noche empieza a cazar a una hora distinta (entre 600 y 6000 ticks despues del anochecer).
- Si el jugador lo **mira directamente** no ataca: se detiene y mira, se gira, se aleja, entra en
  casa, finge comerciar con otro aldeano o finge trabajar. Si va a matar y lo miran, disimula
  (sin espada ni particulas) y espera a que miren a otro lado (maximo 30 segundos).
- Tiene un **nivel de sospecha** (0 a 100, se guarda en el aldeano): sube si lo miras mucho, si te
  quedas pegado a el o si un testigo lo acusa. Con **30** evita acercarse a ti, con **50** cambia
  de zona y con **70** esconde la lista de nombres y limpia la sangre cercana.
- Si lo dejas en paz, la sospecha baja y vuelve a su rutina. Si lo atacas, deja de fingir y se defiende.

## 1.0.0
- Aldeano asesino, comandos `/psycho spawn`, `kill`, `attack`, `window attack`, cuerpos con sangre,
  gritos, nombres, cuerpo bajo la cama, cuerpo del jugador con su skin, rastro de sangre.
