package com.samix.vozbloque.client;

/**
 * Palabras frecuentes al hablar mientras se juega. En el modo "mixto" se añaden a la gramática junto con
 * las palabras de los bloques, para que lo que se dice en una conversación normal tenga dónde "caer" y no se
 * confunda con un bloque.
 */
public final class PalabrasComunes {
	private PalabrasComunes() {}

	public static final String[] LISTA = (
			"a abajo acá acaba acabo adelante adentro afuera agua ahí ahora al algo alguien algún alguna allá allí alto "
			+ "amigo amigos antes aquí arriba así atrás aún aunque ayuda ayúdame "
			+ "bajo bastante bien bueno buena buenas busca buscar buscando "
			+ "cada cae caer caído calla casa casi cerca chaval chavales chicos chico cierto claro coge coger cogido "
			+ "come comer comida como cómo con conmigo contigo contra corre correr cosa cosas creo cual cuál cuando "
			+ "cuándo cuanto cuánto cuidado cueva "
			+ "da dale dame de debajo decir dejad deja del delante dentro desde después detrás di dice dicho digo dijo "
			+ "donde dónde dos "
			+ "el él ella ellos en encima encontrado entonces era eres es esa ese eso espera esperad esta está "
			+ "estaba estamos están estás este esto estoy exacto explorar "
			+ "falta fuera fue fuimos "
			+ "gana ganado gente gracias gran grande guapo "
			+ "ha haber habéis hace hacemos hacer hacia hago hambre has hay he hecho hemos hola hombre hora hoy "
			+ "ido igual increíble ir "
			+ "jajaja jo joder juego jugar "
			+ "la las le les lejos lo los luego "
			+ "mal mala madre mata matar matado me mejor menos mi mí mía mientras mío mira mirad mis momento morir "
			+ "muerto mucho mucha muchos muy mundo "
			+ "nada nadie necesito ni no noche nos nosotros nuestro nueva nuevo nunca "
			+ "o oye otra otro otros "
			+ "para parad pasa pasado pasar pero perdón pica picar pico poco podemos por porque primero pues "
			+ "puedes puedo puede "
			+ "qué que queda quiero quieres quien quién "
			+ "rápido ratito raro "
			+ "sabe sabes sale salir se sé sea seguro ser si sí sido siempre sigue sin sobre socorro solo son soy "
			+ "su sube subir sus "
			+ "también tan tanto te tengo tenemos tener tiene tienes todo toda todos todas toma tranquilo tres tú tu "
			+ "tus "
			+ "un una uno unos unas "
			+ "va vale vais vamos vas vaya veis ven venga veo ver verdad vez vida vosotros voy "
			+ "y ya yo "
			+ "zombi zombie esqueleto creeper aldeano araña enderman espada hacha pala armadura casco botas "
			+ "minar mina construir craftear crafteo cofre portal inventario vida experiencia nivel"
	).split(" ");

	/** ["piedra", "tierra", ..., "[unk]"] */
	public static String gramatica(java.util.Collection<String> palabrasBloques, boolean conComunes) {
		java.util.Set<String> todas = new java.util.TreeSet<>(palabrasBloques);
		if (conComunes) todas.addAll(java.util.List.of(LISTA));
		StringBuilder sb = new StringBuilder("[");
		for (String w : todas) {
			if (w.isBlank()) continue;
			sb.append('"').append(w.replace("\\", "").replace("\"", "")).append("\",");
		}
		sb.append("\"[unk]\"]");
		return sb.toString();
	}
}
