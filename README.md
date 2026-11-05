# Psycho Villager

Mod para **Minecraft Java 1.20.1** con **Forge 47.4.10** (Java 17).

Un aldeano del mundo es un asesino. Cada noche caza y mata a otro aldeano.
Hay que encontrarlo y matarlo.

## Que hace

- Al anochecer (la primera vez en el mundo) un aldeano al azar se convierte en el asesino
  (necesita que haya al menos 2 aldeanos cargados).
- De dia parece un aldeano normal. De noche se arma con una espada de hierro, suelta particulas
  de enfado y persigue a otro aldeano para matarlo.
- El aldeano asesinado puede **gritar** (se oye de lejos y sale un aviso en pantalla).
- Al morir deja su **cadaver** tumbado en el suelo y **manchas de sangre** alrededor.
  Los cadaveres se quitan pegandoles y desaparecen solos a los 5 dias de juego.
- Si le pegas al asesino se defiende y te persigue.
- Cuando lo matas, sale un mensaje para todos los jugadores.

## Cosas extra

- **Todos los aldeanos tienen nombre**, tambien el asesino (no se repiten entre los cargados).
- A veces el asesino **esconde el cuerpo debajo de una cama** cercana.
- Si el asesino **te mata**, queda **tu cuerpo con tu skin** en el suelo. Despues no vuelve a por ti
  hasta que le **pegues otra vez**.
- Los **cuerpos no empujan** ni se dejan empujar.
- El asesino **abre las puertas** de madera mientras caza.
- A veces **suelta un libro con una lista de nombres** de aldeanos (el asesino no sale en ella,
  se cambia en `Config.LIST_INCLUDES_KILLER`).
- A veces **mira una ventana, la rompe y entra agachado** a por su victima.
- Despues de matar deja un **rastro de sangre** al caminar y se oye un **latido** cuando caza cerca de ti.

## Comandos (necesitan ser operador)

| Comando | Que hace |
|---|---|
| `/psycho spawn` | Aparece un aldeano asesino donde estas |
| `/psycho kill` | Mata al aldeano asesino |
| `/psycho attack` | El asesino ataca ahora mismo al aldeano/jugador mas cercano |
| `/psycho window attack` | Va a una ventana, la mira, la rompe, entra agachado y ataca |

## Compilar con GitHub

1. Sube todos los archivos a un repositorio de GitHub.
2. Ve a la pestaña **Actions** y abre la ejecucion **Compilar mod**.
3. Cuando termine (marca verde), abajo en **Artifacts** descarga `psycho-villager-jar`.
4. Dentro esta el `.jar`: ponlo en la carpeta `mods` de tu Minecraft con Forge 1.20.1.

## Ajustes

Todo se cambia en `src/main/java/com/psycho/villager/Config.java` (cuantos mata por noche,
velocidad, vida del asesino, probabilidad de grito, etc.).
