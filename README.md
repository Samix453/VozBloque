# VozBloque — Minecraft pero si decimos el bloque, se borra

Mod para **Minecraft 1.20.1 (Fabric)**. Escucha el micrófono de cada jugador con reconocimiento de voz
**offline en español** (Vosk) y, cuando alguien dice una palabra que aparece en el nombre de un bloque
(tal y como sale en el juego en español), esos bloques desaparecen a su alrededor.

Ejemplos: «piedra» borra Piedra, Piedra lisa, Escaleras de piedra, Ladrillos de piedra…;
«roble» borra Tronco de roble, Tablones de roble, Hojas de roble…; «negro» borra Lana negra, Hormigón negro…

## Instalación (cada jugador y el servidor)

1. Instala **Fabric Loader** para 1.20.1 (https://fabricmc.net/use/installer/).
2. Mete en la carpeta `mods`:
   - **Fabric API** para 1.20.1 (https://modrinth.com/mod/fabric-api)
   - **vozbloque-1.0.0.jar** (de la pestaña *Releases* de este repositorio)
3. El servidor necesita lo mismo (Fabric + Fabric API + VozBloque). Si jugáis en un mundo abierto a LAN, basta con que el anfitrión lo tenga además de cada jugador.

La **primera vez** que abras el juego, el mod descarga solo el modelo de voz en español (~40 MB) y el motor de voz
(~15 MB en Windows) a la carpeta `.minecraft/vozbloque`. Arriba a la izquierda verás el progreso.
Cuando ponga **● Escuchando**, ya está.

## En el juego

- **J**: silenciar / activar tu micrófono (se puede cambiar en Controles).
- `/voz` – estado del micro.
- `/voz probar <frase>` – prueba sin hablar, como si lo hubieras dicho.
- `/voz buscar <palabra>` – qué bloques borraría esa palabra.
- `/voz micros` y `/voz micro <nombre>` – elegir micrófono si no coge el bueno.

Comandos del servidor (operador):

- `/vozbloque activar` | `/vozbloque pausar`
- `/vozbloque radio <horizontal> [vertical]` (vertical `-1` = toda la altura del mundo)
- `/vozbloque zona jugador|todos` – borrar solo alrededor de quien lo dice, o de todos.

## Configuración

- `config/vozbloque-cliente.json`: palabras ignoradas (por defecto «de», «la», «bloque»…), reacción rápida,
  masculino/femenino, micrófono…
- `config/vozbloque-servidor.json`: radio, zona, bloques protegidos (por defecto Piedra base y el portal del End,
  para que el reto se pueda terminar), bloques por tick.
