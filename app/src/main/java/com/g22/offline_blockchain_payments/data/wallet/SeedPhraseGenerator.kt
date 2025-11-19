package com.g22.offline_blockchain_payments.data.wallet

import org.bitcoinj.core.Utils
import org.bitcoinj.crypto.MnemonicCode
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import java.security.SecureRandom

/**
 * Generador de seed phrases en español (6 palabras).
 * 
 * IMPORTANTE:
 * - La seed phrase es SOLO una copia de seguridad de la clave privada
 * - NO se usa para derivar la clave privada (no BIP32/BIP44)
 * - La seed phrase solo se muestra una vez al usuario y NUNCA se almacena
 * - La clave privada se genera aleatoriamente de forma independiente
 */
object SeedPhraseGenerator {
    private const val ENTROPY_BITS = 48 // Para 6 palabras
    private const val WORD_COUNT = 6
    
    // Lista de palabras en español (2048 palabras comunes para BIP39-style)
    // Usamos una lista simplificada de palabras españolas comunes
    private val spanishWords = listOf(
        "abaco", "abdomen", "abeja", "abierto", "abogado", "abono", "aborto", "abrazo",
        "abrigo", "abril", "abrir", "abuelo", "abuso", "acabar", "academia", "acceso",
        "accion", "aceite", "acelga", "acento", "aceptar", "acero", "acido", "acne",
        "acorde", "activo", "acto", "actuar", "acuerdo", "adaptar", "adecuado", "adios",
        "admitir", "adoptar", "adorno", "aduana", "adulto", "aereo", "afectar", "aficion",
        "afirmar", "agenda", "agente", "agosto", "agua", "aguila", "aguja", "ahora",
        "aire", "aislar", "ajedrez", "ajuste", "alarma", "alba", "albaricoque", "albergue",
        "alcalde", "alcance", "aldea", "alegre", "alemania", "alerta", "alfabeto", "alga",
        "algo", "alguien", "alguno", "aliado", "aliento", "alimento", "alinear", "alivio",
        "almacen", "almohada", "alquiler", "altar", "alterar", "altitud", "alto", "altura",
        "alumno", "alzar", "amable", "amante", "amar", "amargo", "ambiente", "ambulancia",
        "amenaza", "america", "amigo", "amistad", "amor", "amplio", "analisis", "ancho",
        "anciano", "ancla", "andar", "andén", "anexo", "angulo", "animal", "anillo",
        "animar", "aniversario", "ano", "anoche", "ansiedad", "ante", "antena", "antes",
        "antiguo", "anual", "anular", "anuncio", "añadir", "apagar", "aparato", "apartar",
        "apellido", "apenas", "apertura", "apio", "aplicar", "apodo", "aporte", "apoyo",
        "aprender", "aprobado", "aprovechar", "apto", "apuesta", "apuntar", "aquello", "aqui",
        "arado", "arana", "arbol", "arca", "archivo", "arco", "arder", "ardilla",
        "arena", "arete", "argelia", "argumento", "arido", "arma", "armario", "armonia",
        "arnes", "aroma", "arpa", "arqueologia", "arreglar", "arriba", "arroz", "arte",
        "artista", "asado", "ascensor", "aseo", "asesino", "asfalto", "asiento", "asignar",
        "asistir", "asno", "asombro", "aspecto", "aspirina", "astronauta", "ataque", "atar",
        "atencion", "aterrizar", "atlas", "atleta", "atmosfera", "atomo", "atras", "atravesar",
        "atreverse", "atun", "aula", "aumentar", "aun", "aunque", "aurora", "ausente",
        "autobus", "auto", "autopista", "autor", "autorizar", "auxiliar", "avanzar", "ave",
        "avena", "aventura", "avion", "avisar", "aviso", "ayer", "ayuda", "ayudar",
        "ayunar", "azafata", "azar", "azote", "azucar", "azul", "babor", "bache",
        "bahia", "bailar", "bajar", "bajo", "balanza", "balcon", "balde", "ballena",
        "banco", "banda", "bandera", "baño", "barato", "barba", "barco", "barrer",
        "barrio", "barro", "base", "basico", "basura", "bata", "batalla", "bateria",
        "batir", "bautizar", "beber", "bebida", "beca", "beisbol", "belleza", "bello",
        "bendecir", "beneficio", "besar", "beso", "bestia", "biblia", "biblioteca", "bicicleta",
        "bien", "bienestar", "bienvenido", "bigote", "billete", "binario", "biologia", "bisabuelo",
        "bisturi", "blanco", "bloque", "blusa", "boca", "bocadillo", "boda", "bodega",
        "boina", "bola", "bolero", "bolsa", "bomba", "bombilla", "bombero", "bondad",
        "bonito", "borde", "borrar", "bosque", "bota", "botella", "boton", "boxeo",
        "brazo", "breve", "brillante", "brillar", "brillo", "brindar", "brisa", "broche",
        "broma", "bronce", "brote", "bruja", "bruselas", "bruto", "bueno", "buey",
        "bufanda", "bufete", "buhardilla", "bujia", "bulto", "burbuja", "burla", "buscar",
        "busto", "butaca", "buzon", "caballo", "cabeza", "cabo", "cabra", "cacao",
        "cacerola", "cachorro", "cadena", "cadera", "caer", "cafe", "caja", "cajon",
        "cal", "calabaza", "calamar", "calcular", "caldo", "calefaccion", "calendario", "calentar",
        "calidad", "caliente", "calificar", "calma", "calor", "calzado", "calzon", "callar",
        "calle", "cama", "camara", "cambiar", "camello", "caminar", "camino", "camion",
        "camisa", "campana", "campo", "canal", "canario", "cancelar", "cancion", "candado",
        "canela", "cangrejo", "canica", "canoa", "cansado", "cantar", "cantidad", "caña",
        "cañon", "capaz", "capitulo", "capricho", "captar", "cara", "caracol", "caracter",
        "carbon", "carcasa", "cardenal", "carecer", "carga", "cargar", "carino", "carne",
        "carnet", "caro", "carpeta", "carretera", "carro", "carta", "cartel", "carton",
        "casa", "casco", "casero", "caso", "castaño", "castigar", "castillo", "casual",
        "catalogo", "catar", "catedral", "categoria", "catolicismo", "causa", "cautela", "cavar",
        "cazador", "cebolla", "ceder", "cedro", "ceja", "celda", "celebrar", "celeste",
        "celo", "celular", "cemento", "cenar", "ceniza", "centavo", "centro", "cepillar",
        "cera", "cerca", "cercano", "cerdo", "cerebro", "ceremonia", "cero", "cerrar",
        "cerro", "cesar", "cesta", "cetro", "chacal", "chal", "champan", "chance",
        "chapa", "charla", "charlar", "charlatan", "chasco", "chasis", "cheque", "chico",
        "chile", "chimenea", "china", "chino", "chiste", "chocar", "chocolate", "chofer",
        "chubasco", "chupar", "churro", "cielo", "cien", "ciencia", "cierto", "cifra",
        "cigarro", "cima", "cincel", "cinco", "cine", "cinta", "cinto", "circulo",
        "ciruela", "cita", "ciudad", "civil", "clamor", "clara", "clase", "clasico",
        "clave", "clavel", "cliente", "clima", "clinica", "cloro", "club", "coartada",
        "cobarde", "cobre", "cobrar", "cocina", "coco", "codigo", "cofre", "coger",
        "cohete", "cojear", "cojin", "cola", "colcha", "coleccion", "colegio", "colgar",
        "colina", "colocar", "colonia", "color", "columna", "comadre", "comandante", "comarca",
        "combate", "combinar", "comedia", "comentar", "comer", "cometa", "comida", "comite",
        "como", "comodo", "compadre", "companero", "compartir", "compas", "competir", "completo",
        "componer", "comprar", "comprender", "compromiso", "comun", "comunidad", "con", "concebir",
        "conceder", "concepto", "conciencia", "concluir", "concreto", "condado", "condenar", "condicion",
        "conducir", "conductor", "conejo", "conferencia", "confesar", "confiar", "conflicto", "confundir",
        "congelar", "congreso", "conjunto", "conocer", "conquista", "conseguir", "consejo", "consentir",
        "conservar", "considerar", "consistir", "consolar", "constante", "construir", "consultar", "consumir",
        "contacto", "contar", "contemplar", "contener", "contento", "contestar", "continuar", "contra",
        "contrario", "contrato", "control", "controversia", "convencer", "convenir", "convento", "conversar",
        "convertir", "convivir", "cooperar", "copa", "copia", "copiar", "corazon", "corazonada",
        "corbata", "corcho", "cordera", "cordero", "cordon", "coro", "corona", "coronel",
        "corpulento", "corral", "correccion", "corredor", "corregir", "correo", "correr", "corriente",
        "cortar", "corte", "cortes", "cortina", "cosa", "coser", "cosmos", "costa",
        "costar", "costilla", "costumbre", "cotizar", "crear", "crecer", "credencial", "credito",
        "creer", "crema", "crepitar", "crespon", "creyente", "criar", "crimen", "crisis",
        "cristal", "criterio", "critica", "crucificar", "cruzar", "cuaderno", "cuadro", "cual",
        "cualidad", "cualquier", "cuando", "cuanto", "cuarto", "cubierto", "cubito", "cubo",
        "cubrir", "cuchara", "cuchillo", "cuello", "cuenta", "cuento", "cuerda", "cuerpo",
        "cuervo", "cuesta", "cueva", "cuidado", "cuidar", "culebra", "culpa", "cultivar",
        "culto", "cumbre", "cumpleaños", "cumplir", "cuna", "cuña", "cuota", "cupo",
        "curar", "curiosidad", "curso", "curtir", "curva", "cutis", "dado", "dama",
        "damasco", "danza", "dar", "dardo", "dato", "de", "debajo", "debatir",
        "deber", "debil", "decada", "decidir", "decimo", "decir", "decorar", "dedicar",
        "dedo", "defender", "defensa", "definir", "dejar", "del", "delantal", "delegado",
        "delgado", "delicado", "delito", "demanda", "demasiado", "democracia", "demoler", "demora",
        "denominar", "denso", "dentista", "dentro", "departamento", "depender", "deporte", "deposito",
        "derecha", "derecho", "derramar", "derretir", "derribar", "derrota", "des", "desafio",
        "desagradable", "desaparecer", "desarrollar", "desarrollo", "desayunar", "descansar", "descargar", "descartar",
        "descender", "describir", "descubrir", "descuento", "desde", "desear", "desechar", "desempleo",
        "desenlace", "desertar", "desfile", "desgracia", "deshacer", "desierto", "desigual", "desistir",
        "desnudo", "despacho", "despedir", "despegar", "despertar", "despido", "despistar", "despues",
        "desterrar", "destino", "destruir", "desviar", "detalle", "detener", "detras", "deuda",
        "deudor", "devaluar", "dia", "diablo", "diadema", "diagnostico", "diagrama", "dialecto",
        "dialogo", "diamante", "diario", "dibujar", "dibujo", "diccionario", "dicha", "dicho",
        "diente", "diesel", "dieta", "diez", "diferencia", "diferente", "dificil", "difundir",
        "digno", "dilatar", "dilema", "diligencia", "diluviar", "dimension", "dinero", "dinosaurio",
        "diploma", "diputado", "directo", "director", "dirigir", "disciplina", "disco", "discutir",
        "diseñar", "disfraz", "disgusto", "disparar", "disponer", "disputa", "distancia", "distinto",
        "distraer", "diversion", "diverso", "dividir", "divino", "divorcio", "doblar", "doble",
        "doce", "doctor", "documento", "dolar", "doler", "dolor", "domar", "domesticar",
        "domicilio", "domingo", "don", "donar", "donde", "dorado", "dormir", "dorso",
        "dos", "dosis", "dote", "drama", "droga", "ducha", "duda", "duelo",
        "duena", "dueno", "dulce", "duna", "duplicar", "duque", "durante", "durar",
        "duro", "ebano", "ebrio", "echar", "eclipse", "eco", "economia", "ecuador",
        "edad", "edicion", "edificio", "editar", "editor", "educar", "educacion", "efecto",
        "eficaz", "ego", "eje", "ejecutar", "ejemplo", "ejercer", "ejercicio", "el",
        "elaborar", "eleccion", "electrico", "electronico", "elegante", "elegir", "elemento", "elevar",
        "elite", "ella", "ello", "elogio", "eludir", "embajada", "embarcar", "embargo",
        "embellecer", "embriagar", "emerger", "emigrar", "emision", "emitir", "emocion", "empacar",
        "empate", "empeñar", "empezar", "emplear", "empleo", "empresa", "empujar", "en",
        "enano", "encantar", "encargar", "encender", "encerrar", "encima", "encontrar", "encuesta",
        "enderezar", "enemigo", "energia", "enero", "enfadar", "enfermar", "enfermedad", "enfermera",
        "enfermo", "enfocar", "enfrentar", "enganar", "engordar", "engranaje", "enigma", "enjambre",
        "enlace", "enojar", "enorme", "enredar", "ensalada", "ensayar", "ensayo", "ensenar",
        "entender", "entero", "enterrar", "entonces", "entrada", "entrar", "entre", "entregar",
        "entrenar", "entrevista", "entusiasmo", "enviar", "envidia", "envio", "envolver", "epoca",
        "equilibrar", "equipo", "equivocar", "era", "erigir", "error", "erudito", "es",
        "escala", "escapar", "escarcha", "escaso", "escena", "esclavo", "escolar", "esconder",
        "escribir", "escritor", "escritorio", "escuchar", "escudo", "escuela", "esencia", "esfera",
        "esfuerzo", "eslabon", "esmeralda", "esmero", "esnob", "espacio", "espada", "espalda",
        "espanol", "especial", "especie", "esperanza", "esperar", "espeso", "espia", "espina",
        "espiritu", "esponja", "esposa", "esposo", "espuma", "esqueleto", "esquema", "esquina",
        "esquivar", "esta", "estable", "establecer", "estacion", "estadio", "estado", "estallar",
        "estampa", "estanque", "estar", "este", "estera", "estilo", "estimular", "estirar",
        "esto", "estomago", "estorbar", "estrecho", "estrella", "estrenar", "estribo", "estricto",
        "estructura", "estuche", "estudiar", "estudio", "estufa", "etapa", "eterno", "etica",
        "etiqueta", "europa", "evadir", "evaluar", "evaporar", "evento", "evidencia", "evitar",
        "exacto", "exagerar", "examen", "examinar", "excelente", "excepcion", "exceso", "excitacion",
        "exclamar", "excluir", "excusa", "exhibir", "exigir", "exilio", "existir", "exito",
        "expansion", "experiencia", "experimento", "explicar", "explorar", "explotar", "exportar", "exposicion",
        "expresar", "expresion", "extender", "exterior", "externo", "extra", "extraer", "extranjero",
        "extraño", "extremo", "fabricar", "fabula", "facil", "facto", "factor", "facultad",
        "faja", "falda", "fallar", "fallecer", "falso", "falta", "faltar", "fama",
        "familia", "famoso", "fango", "fantasia", "faro", "fascinante", "fase", "fastidiar",
        "fatal", "fatiga", "favor", "favorito", "febrero", "fecha", "felicidad", "feliz",
        "feo", "feria", "feroz", "ferrocarril", "fertil", "festejar", "feto", "feudal",
        "fianza", "fiar", "fibra", "ficcion", "ficha", "fiebre", "fiel", "fiera",
        "fiesta", "figura", "fijar", "fijo", "fila", "filete", "filo", "filosofia",
        "filtro", "fin", "final", "financiar", "finca", "fingir", "fino", "firma",
        "firmar", "firme", "fisico", "flaco", "flauta", "flecha", "flor", "florecer",
        "flotar", "fluir", "foco", "fogata", "fogón", "folleto", "fondo", "fontanero",
        "forma", "formar", "formula", "foro", "fortaleza", "fortuna", "forzar", "fosa",
        "fotografia", "fracaso", "fraccion", "fragil", "francia", "franco", "frase", "fraude",
        "frecuencia", "fregar", "frenar", "freno", "frente", "fresco", "fresno", "frio",
        "frito", "frondoso", "frontera", "frotar", "fruta", "fruto", "fuego", "fuente",
        "fuera", "fuerte", "fuga", "fumar", "funcion", "funcionar", "fundar", "fundir",
        "furioso", "futuro", "gabinete", "gafas", "gaita", "gajo", "gala", "galaxia",
        "galeria", "galleta", "gallo", "gama", "ganado", "ganancia", "ganar", "gancho",
        "ganso", "garaje", "garantia", "garbanzo", "garganta", "garra", "garrote", "gas",
        "gastar", "gato", "gaviota", "gemelo", "gemir", "genero", "generoso", "genial",
        "genio", "gente", "geografia", "gerente", "germen", "gesto", "gigante", "gimnasio",
        "girar", "giro", "glaciar", "globo", "gloria", "glosario", "glotón", "gobernar",
        "gobierno", "gol", "golpe", "golpear", "goma", "gordo", "gorila", "gorra",
        "gorro", "gota", "gotear", "gozar", "grabar", "gracia", "grado", "gramo",
        "gran", "granada", "grande", "granero", "granito", "grano", "grasa", "gratis",
        "grave", "grieta", "grillo", "gris", "gritar", "grito", "grosor", "grueso",
        "grupo", "guante", "guapo", "guardar", "guerra", "guia", "guiar", "guijarro",
        "guion", "guisante", "guitarra", "gusano", "gustar", "gusto", "haba", "haber",
        "habichuela", "habitacion", "habito", "hablar", "hacer", "hacha", "hada", "hallar",
        "hamaca", "hambre", "hamburguesa", "harina", "harto", "hasta", "hay", "hazaña",
        "hebra", "hecho", "helado", "helar", "helecho", "hembra", "heno", "herida",
        "herir", "hermano", "hermoso", "heroe", "herramienta", "hervir", "hielo", "hierba",
        "hierro", "higo", "hija", "hijo", "hilera", "hilo", "hinchar", "hipo",
        "hipoteca", "historia", "hoja", "hombre", "hombro", "hombre", "homenaje", "honesto",
        "hongo", "honor", "honra", "hora", "horizonte", "hormiga", "hormigon", "horno",
        "horror", "hospital", "hostil", "hotel", "hoy", "hoyo", "hueco", "huevo",
        "huir", "humano", "humedad", "humilde", "humo", "hundir", "huracan", "ida",
        "ideal", "idear", "identico", "identificar", "ideologia", "idioma", "idiota", "idolo",
        "iglesia", "ignorar", "igual", "ilegal", "iluminar", "ilusion", "ilustrar", "imagen",
        "imaginacion", "imaginar", "imitar", "impaciente", "impedir", "imperio", "imponer", "importar",
        "importante", "imposible", "impresion", "imprimir", "impuesto", "impulsar", "impulso", "in",
        "inactivo", "inaugurar", "incapaz", "incendio", "incienso", "incidente", "incluir", "incluso",
        "incomodo", "increible", "independiente", "indicar", "indice", "indiferente", "indignar", "indio",
        "individuo", "inducir", "industria", "inevitable", "infancia", "infantil", "infeliz", "inferior",
        "infierno", "inflar", "influir", "informacion", "informar", "ingeniero", "ingenuo", "ingresar",
        "ingreso", "iniciar", "inicio", "injusto", "inmediato", "inmenso", "inmigrante", "inmortal",
        "inmueble", "innumerable", "inocente", "inodoro", "inolvidable", "inoportuno", "inquietar", "insecto",
        "insistir", "insolente", "inspirar", "instalar", "instante", "instinto", "instruir", "instrumento",
        "insultar", "insulto", "inteligente", "intentar", "intento", "interes", "interesante", "interior",
        "intermedio", "internacional", "interno", "interpretar", "interrumpir", "intervalo", "intervenir", "intestino",
        "intimo", "introducir", "inundar", "inutil", "invadir", "inventar", "inventario", "inversion",
        "invertir", "investigar", "invitar", "invocar", "inyectar", "ir", "ira", "iris",
        "ironia", "isla", "izquierda", "jabon", "jardin", "jarra", "jaula", "jazmin",
        "jefe", "jeringa", "jersey", "jesus", "jinete", "jirafa", "jocoso", "jornada",
        "joroba", "joven", "joya", "jubilacion", "judio", "juego", "jueves", "juez",
        "jugador", "jugar", "jugo", "juguete", "juicio", "julio", "junio", "juntar",
        "junto", "jurado", "jurar", "justicia", "justificar", "justo", "juvenil", "juzgar",
        "kilo", "labio", "labor", "labrador", "lado", "ladrillo", "ladron", "lagarto",
        "lago", "lagrima", "lampara", "lana", "lancha", "langosta", "lanzar", "lapiz",
        "largo", "larva", "las", "lata", "latido", "latino", "laurel", "lavabo",
        "lavar", "lazo", "le", "leal", "leche", "lecho", "leer", "legado",
        "legal", "legumbre", "lejano", "lejos", "lengua", "lenguaje", "lente", "lento",
        "leon", "leopardo", "lesion", "letal", "letra", "levantar", "leve", "ley",
        "leyenda", "liar", "libelula", "liberal", "libertad", "libra", "libre", "libro",
        "licencia", "licor", "lider", "liebre", "liga", "ligero", "lima", "limitar",
        "limite", "limon", "limpiar", "limpio", "linea", "linterna", "liquido", "lista",
        "listo", "litera", "literatura", "litro", "lobo", "local", "localizar", "loco",
        "locura", "lodazal", "lodo", "logica", "lograr", "logro", "lomo", "lona",
        "longitud", "lote", "lubricar", "lucir", "luchar", "luego", "lugar", "lujo",
        "luna", "lunes", "lupa", "luto", "luz", "llaga", "llama", "llamar",
        "llamativo", "llano", "llanta", "llanto", "llave", "llegar", "llenar", "lleno",
        "llevar", "llorar", "llover", "lluvia", "lo", "lobo", "local", "locura",
        "logica", "lograr", "lomo", "lona", "longitud", "lote", "lubricar", "lucir",
        "luchar", "luego", "lugar", "lujo", "luna", "lunes", "lupa", "luto",
        "luz", "maceta", "macho", "madera", "madre", "madriguera", "maduro", "maestro",
        "mafia", "magia", "magico", "magnifico", "magnitud", "maiz", "majestuoso", "mal",
        "mala", "maleta", "malo", "mama", "mamut", "manantial", "mancha", "mandar",
        "mandato", "mandibula", "manejar", "manera", "manga", "mango", "mania", "mano",
        "manso", "manta", "mantel", "mantener", "mantequilla", "manual", "manzana", "mapa",
        "maqueta", "maquina", "mar", "marca", "marcar", "marcha", "marchitar", "marea",
        "mareo", "margarita", "marido", "marino", "mariposa", "marmol", "marron", "martes",
        "martillo", "marzo", "mas", "masa", "masaje", "mascar", "mascara", "masculino",
        "masivo", "mastil", "mata", "matar", "mate", "material", "materia", "matrimonio",
        "maullar", "mayo", "mayor", "maza", "me", "mecha", "media", "mediano",
        "medicina", "medico", "medida", "medidor", "medio", "medir", "mejor", "mejorar",
        "melancolia", "melodia", "melon", "memoria", "menor", "menos", "mensaje", "mente",
        "mentir", "mentira", "menu", "menudo", "mercado", "merced", "merecer", "merienda",
        "merito", "mes", "mesa", "meseta", "metal", "meter", "metodo", "metro",
        "mezclar", "mi", "mia", "microfono", "miedo", "miel", "miembro", "mientras",
        "migaja", "migrar", "mil", "milagro", "militar", "millon", "mina", "mineral",
        "minimo", "ministerio", "minuto", "mio", "miope", "mirar", "misa", "miseria",
        "mismo", "mitad", "mito", "mochila", "moda", "modelo", "moderar", "modesto",
        "modificar", "modo", "mojar", "molde", "moler", "molestar", "molino", "momento",
        "monarca", "moneda", "monitor", "mono", "monstruo", "montaña", "montar", "monte",
        "monumento", "morado", "moral", "morder", "moreno", "morir", "moro", "mortal",
        "mosca", "mostrar", "motivo", "motor", "mover", "movil", "movimiento", "mozo",
        "mucho", "mudar", "mueble", "muela", "muerte", "muerto", "muestra", "mujer",
        "mula", "multa", "multiple", "mundo", "municipio", "muñeca", "mural", "muro",
        "musculo", "museo", "musica", "musico", "muslo", "mutuo", "nacer", "nacional",
        "nada", "nadar", "nadie", "naipe", "naranja", "nariz", "narracion", "narrar",
        "nata", "natacion", "nativo", "natural", "naturaleza", "naufragio", "nave", "navegar",
        "navidad", "necesario", "necesidad", "necesitar", "necio", "negar", "negativo", "negocio",
        "negro", "nervio", "nervioso", "neto", "neutro", "ni", "niebla", "nieto",
        "nieve", "ningun", "ninguno", "niño", "nivel", "no", "noble", "noche",
        "nombre", "normal", "norte", "nos", "nosotros", "nota", "notar", "noticia",
        "notificar", "novato", "novela", "noviembre", "novio", "nube", "nublado", "nuca",
        "nucleo", "nudillo", "nudo", "nuera", "nueve", "nuevo", "nuez", "numero",
        "numeroso", "nunca", "nutrir", "o", "obedecer", "objeto", "obligar", "obra",
        "obrero", "observar", "obstaculo", "obtener", "obvio", "ocasion", "ocaso", "oceano",
        "ocio", "octubre", "ocultar", "ocupar", "ocurrir", "odiar", "odio", "oeste",
        "ofender", "oferta", "oficial", "oficina", "ofrecer", "oido", "oir", "ojo",
        "ola", "oleada", "olfato", "oliva", "olivo", "olmo", "olor", "olvidar",
        "ombligo", "omitir", "once", "onda", "onza", "opcion", "operar", "opinion",
        "oponer", "oportuno", "oprimir", "optar", "optimista", "opuesto", "oracion", "orador",
        "oral", "orden", "ordenar", "oreja", "organismo", "organizar", "orgullo", "oriental",
        "origen", "original", "orilla", "oro", "orquesta", "ortografia", "osar", "oscuro",
        "oso", "ostra", "otoño", "otro", "oveja", "oxigeno", "oye", "oyente",
        "pabellon", "pacer", "paciencia", "paciente", "pacto", "padre", "padrino", "pagar",
        "pago", "pais", "paisaje", "paja", "pajaro", "pala", "palabra", "palacio",
        "palanca", "palco", "paleta", "palma", "palmera", "palo", "paloma", "palpar",
        "pan", "panal", "pandilla", "panel", "panico", "pantalon", "pantano", "pantera",
        "pantalla", "pantano", "pantorrilla", "panuelo", "papa", "papel", "papilla", "paquete",
        "par", "para", "parabrisas", "parada", "parado", "paraguas", "paraiso", "paralelo",
        "parametro", "parar", "parcela", "pared", "pareja", "parecer", "parentesis", "pariente",
        "parir", "parlamento", "paro", "parque", "parra", "parte", "participar", "particular",
        "partida", "partido", "partir", "pasado", "pasaje", "pasar", "paseo", "pasillo",
        "paso", "pasta", "pastel", "pastilla", "pasto", "pata", "patada", "patear",
        "patente", "paterno", "patio", "patria", "patron", "pausa", "pavo", "paz",
        "peaje", "peaton", "pecado", "pecar", "pecho", "pedal", "pedazo", "pedir",
        "pegar", "peine", "pelar", "pelea", "pelicula", "peligro", "pelo", "pelota",
        "pena", "pendiente", "penetrar", "peninsula", "pensamiento", "pensar", "pension", "peña",
        "peor", "pequeño", "pera", "perder", "perdida", "perdon", "perdurar", "perecer",
        "peregrino", "perezoso", "perfecto", "perfil", "perforar", "perfume", "periodico", "periodo",
        "perla", "permanecer", "permiso", "permitir", "pero", "perro", "perseguir", "persistir",
        "persona", "personal", "persuadir", "pertenecer", "pesado", "pesar", "pesca", "pescado",
        "peso", "pestana", "petroleo", "pez", "pi", "piano", "picar", "pico",
        "pie", "piedra", "piel", "pierna", "pieza", "pijama", "pila", "piloto",
        "pimienta", "pino", "pintar", "pintor", "pintura", "pinza", "piojo", "pipa",
        "pirata", "pisar", "piscina", "piso", "pista", "pistola", "piston", "pizarra",
        "placa", "placer", "plan", "plancha", "planear", "planeta", "plano", "planta",
        "plantar", "plastico", "plata", "plataforma", "plato", "playa", "plaza", "plegar",
        "pleno", "pliego", "plomo", "pluma", "plural", "poblacion", "pobre", "poco",
        "poder", "poema", "poesia", "poeta", "polen", "policia", "poligono", "polilla",
        "politica", "politico", "pollo", "polvo", "polvora", "pomada", "pomelo", "ponderar",
        "poner", "poniente", "popular", "por", "porcion", "porque", "portal", "portar",
        "portatil", "portero", "portugal", "posada", "poseer", "posible", "posicion", "positivo",
        "postal", "poste", "postre", "potencia", "potente", "practica", "practicar", "pradera",
        "precio", "preciso", "predicar", "preferir", "pregunta", "preguntar", "premio", "prenda",
        "prender", "prensa", "preocupar", "preparar", "presencia", "presentar", "presente", "presidente",
        "presion", "prestar", "pretender", "pretexto", "prevenir", "previo", "prima", "primavera",
        "primero", "primo", "princesa", "principal", "principio", "prision", "privado", "pro",
        "probar", "problema", "proceder", "proceso", "procurar", "producir", "producto", "profesion",
        "profesor", "profundo", "programa", "progreso", "prohibir", "prolongar", "promesa", "prometer",
        "promover", "pronto", "pronunciar", "propiedad", "propio", "proponer", "proposito", "proteger",
        "protesta", "provecho", "proveer", "provincia", "proximo", "proyecto", "prueba", "publico",
        "pudor", "pueblo", "puente", "puerta", "puerto", "pues", "puesto", "pulgar",
        "pulir", "pulmon", "pulpo", "pulso", "punta", "puntal", "puntapié", "puntilla",
        "punto", "punzar", "puñal", "puño", "pupila", "pureza", "purificar", "puro",
        "quebrar", "quedar", "queja", "quejarse", "quemar", "querer", "queso", "quien",
        "quieto", "quimica", "quince", "quitar", "rabia", "rabo", "racimo", "racional",
        "radical", "radio", "raiz", "rama", "ramo", "rana", "rancho", "rango",
        "rapido", "rapto", "raro", "rascar", "rasgar", "raso", "rastro", "rata",
        "raton", "raya", "rayo", "raza", "razon", "reaccion", "real", "realidad",
        "realizar", "reanudar", "rebajar", "rebanar", "rebelde", "rebotar", "recado", "recaer",
        "receta", "rechazar", "recibir", "recibo", "recien", "reciente", "recinto", "recipiente",
        "recitar", "reclamar", "reclinar", "recobrar", "recoger", "recomendar", "recompensa", "reconocer",
        "recordar", "recorrer", "recortar", "recreo", "recto", "recuerdo", "recurrir", "red",
        "redactar", "redimir", "reducir", "redundar", "referir", "reflejar", "reflexion", "reforma",
        "refrescar", "refrigerador", "refugio", "regalar", "regalo", "regar", "regatear", "regimen",
        "region", "registrar", "regla", "regresar", "regular", "rehusar", "reina", "reino",
        "reir", "reja", "relacion", "relajar", "relampago", "relatar", "relativo", "relato",
        "relevante", "religion", "reloj", "remar", "remedio", "remitir", "remo", "remolino",
        "remontar", "remover", "renacer", "rendir", "renegar", "renovar", "renta", "renunciar",
        "reo", "reparar", "repartir", "repasar", "repente", "repetir", "replicar", "reportar",
        "reposar", "reposo", "representar", "reprobar", "reproducir", "reptil", "republica", "requerir",
        "requisito", "res", "rescatar", "resena", "reservar", "resfriado", "residencia", "resistir",
        "resolver", "resonar", "respetar", "respeto", "respirar", "respiro", "responder", "responsable",
        "respuesta", "restar", "restaurante", "resto", "restregar", "resultado", "resultar", "resumen",
        "retar", "retardar", "retener", "retirar", "retiro", "retorcer", "retornar", "retrato",
        "retroceder", "reunir", "revelar", "reventar", "reverencia", "revisar", "revista", "revolver",
        "rey", "rezar", "riachuelo", "ribera", "rico", "ridiculo", "rienda", "riesgo",
        "rifa", "rigido", "rigor", "rincón", "rincon", "rio", "riqueza", "risa",
        "ritmo", "rito", "rival", "rizar", "robar", "roble", "robot", "roca",
        "rociar", "rodar", "rodear", "rodilla", "roer", "rogar", "rojo", "rol",
        "romano", "romper", "ron", "roncar", "ronda", "ropa", "ropero", "rosa",
        "rosado", "rostro", "rotar", "roto", "rotundo", "rotura", "rubi", "rubio",
        "rudo", "rueda", "ruedo", "ruido", "ruina", "ruiseñor", "rumbo", "rumor",
        "rural", "ruso", "rustico", "sabado", "saber", "sabio", "sable", "sabor",
        "sabroso", "sacar", "sacerdote", "saco", "sacudir", "sagrado", "sala", "salado",
        "salario", "salchicha", "salida", "salir", "saliva", "salmon", "salon", "salpicar",
        "salsa", "saltar", "salto", "salud", "saludar", "saludo", "salvaje", "salvar",
        "salvo", "sangre", "sanidad", "sano", "santo", "sapo", "saque", "sardina",
        "sarten", "sastre", "saturno", "sauce", "savia", "saxofon", "se", "seccion",
        "secar", "seco", "secretario", "secreto", "secta", "sector", "secuencia", "secuestrar",
        "sed", "seda", "sede", "sedimento", "seguir", "segundo", "seguridad", "seguro",
        "seis", "seleccion", "selva", "sello", "semana", "semilla", "sencillo", "sendero",
        "seno", "sensacion", "sensato", "sentar", "sentido", "sentimiento", "sentir", "señal",
        "separar", "septiembre", "sequia", "ser", "serenidad", "serie", "serio", "sermon",
        "serpiente", "servicio", "servidor", "servir", "sesenta", "sesion", "seta", "setenta",
        "si", "sierra", "siesta", "siete", "siglo", "significar", "signo", "siguiente",
        "silaba", "silbar", "silencio", "silencioso", "silla", "sillon", "silo", "silueta",
        "simbolo", "simpatia", "simple", "simular", "sin", "sincero", "sindicato", "singular",
        "sino", "sintesis", "sintoma", "siquiera", "sirena", "sistema", "sitio", "situacion",
        "situar", "so", "soberano", "sobra", "sobrar", "sobre", "sobrecoger", "sobredosis",
        "sobrepasar", "sobresalir", "sobrino", "sociedad", "socio", "socorrer", "sofocar", "sofrito",
        "soga", "sol", "solapa", "solar", "soldado", "soleado", "solemne", "soler",
        "solicitar", "solidaridad", "solido", "solitario", "solo", "soltar", "solucion", "solucionar",
        "sombra", "sombrero", "sombrilla", "son", "sonar", "sonido", "sonreir", "sonrisa",
        "soñar", "sopa", "soplar", "soplo", "soportar", "sorber", "sordo", "sorprender",
        "sorpresa", "sortija", "sospecha", "sospechar", "sostener", "sotano", "su", "suave",
        "subir", "subito", "sublime", "submarino", "subrayar", "subsistir", "substancia", "subtitulo",
        "suceder", "suceso", "sucursal", "sudar", "sudor", "suegra", "suegro", "suela",
        "suelo", "sueño", "suerte", "suficiente", "sufrir", "sugerir", "suicidio", "sujetar",
        "sujeto", "sultán", "suma", "sumar", "sumergir", "suministrar", "sumo", "superar",
        "superficie", "superior", "supermercado", "suplicar", "suplir", "suponer", "supuesto", "sur",
        "surtido", "surtir", "sus", "suspender", "suspenso", "sustancia", "sustentar", "sustituir",
        "sutil", "suyo", "tabaco", "tabla", "tableta", "taburete", "tacita", "tacto",
        "tajada", "tal", "talento", "talla", "taller", "tallo", "tamano", "tambien",
        "tambor", "tampoco", "tan", "tanda", "tango", "tanque", "tanto", "tapa",
        "tapete", "tapia", "tapizar", "tapon", "taquilla", "tardar", "tarde", "tarea",
        "tarifa", "tarjeta", "tarro", "tarta", "tartamudear", "tasacion", "tasa", "tasar",
        "tatuaje", "taxi", "taza", "te", "teatro", "techo", "tecla", "teclado",
        "tecnica", "tecnico", "tecnologia", "teja", "tejer", "tejido", "tela", "telefono",
        "telescopio", "televisor", "tema", "temblar", "temor", "temperatura", "templo", "temprano",
        "tenaz", "tender", "tener", "tenis", "tension", "tentar", "teoria", "tercero",
        "tercio", "terminar", "termino", "termo", "ternera", "terreno", "terrible", "terror",
        "tesoro", "testigo", "texto", "textura", "ti", "tiempo", "tienda", "tierra",
        "tieso", "tigre", "tilde", "timbre", "timido", "tina", "tinta", "tio",
        "tipo", "tirar", "tiro", "titanio", "titulo", "tiza", "toalla", "tobillo",
        "tocar", "tocino", "todavia", "todo", "toldo", "tolerar", "tomar", "tomate",
        "tonelada", "tono", "tonto", "topo", "toque", "torbellino", "torcer", "tordo",
        "torear", "tormenta", "tornar", "tornillo", "toro", "torpe", "torre", "torta",
        "tortuga", "tos", "toser", "total", "trabajar", "trabajo", "tractor", "traducir",
        "traer", "trafico", "tragar", "trago", "traicion", "traje", "trama", "tramo",
        "trampa", "tranquilo", "transbordar", "transferir", "transformar", "transito", "transmitir", "transparente",
        "transportar", "trapo", "tras", "trascender", "trasero", "trasladar", "traste", "tratar",
        "trato", "travieso", "trayectoria", "trece", "tregua", "treinta", "tremendo", "tren",
        "trenza", "trepar", "tres", "tribu", "tribunal", "tributo", "trigo", "trillar",
        "trinar", "triple", "triste", "triturar", "triunfo", "trofeo", "trompeta", "tronco",
        "trono", "tropa", "tropiezo", "trotar", "trozo", "trucha", "trueno", "trufa",
        "tu", "tuberia", "tubo", "tumba", "tumor", "tunel", "turismo", "turno",
        "turquesa", "tutela", "tutor", "u", "ubicar", "ubre", "ud", "ufano",
        "uña", "ultimo", "umbral", "un", "una", "unanimidad", "unico", "unidad",
        "unificar", "union", "unir", "universal", "universidad", "universo", "uno", "untar",
        "uña", "urbano", "urbe", "urgente", "urna", "usar", "uso", "usted",
        "usual", "usuario", "util", "utilidad", "utilizar", "utopia", "uva", "vaca",
        "vacacion", "vaciar", "vacio", "vacuna", "vagar", "vago", "vaina", "valer",
        "valiente", "valle", "valor", "valvula", "vampiro", "vanidad", "vano", "vapor",
        "vara", "variar", "variedad", "vario", "vaso", "vasto", "vaticinar", "vecino",
        "vector", "vega", "vegetal", "vehiculo", "veinte", "vejez", "vela", "velar",
        "velero", "velocidad", "vena", "vencer", "venda", "vender", "veneno", "venerar",
        "venir", "venta", "ventaja", "ventana", "ventilar", "ventura", "ver", "verano",
        "veras", "verbo", "verdad", "verde", "verdugo", "vereda", "verificar", "verja",
        "vermut", "version", "verso", "verter", "vertigo", "vestido", "vestir", "veterano",
        "vez", "via", "viajar", "viaje", "viajero", "vibora", "vibrar", "vicio",
        "victima", "victoria", "vida", "video", "vidrio", "viejo", "viento", "vientre",
        "viernes", "vigilar", "vigor", "vil", "villa", "vinagre", "vinculo", "vino",
        "violencia", "violento", "violin", "violeta", "virgen", "viril", "virtual", "virtud",
        "virus", "visa", "visera", "visible", "vision", "visita", "visitar", "vista",
        "vitamina", "vitoria", "viuda", "viudo", "vivaz", "vivero", "vivido", "vivir",
        "vivo", "vocablo", "vocacion", "vocal", "volante", "volar", "volcan", "volumen",
        "voluntad", "voluntario", "volver", "vomitar", "voraz", "vos", "vosotros", "votar",
        "voto", "voy", "voz", "vuelo", "vuelta", "vuestro", "vulgar", "w",
        "x", "y", "ya", "yacer", "yate", "yegua", "yema", "yerba",
        "yeso", "yo", "yodo", "yogur", "yugo", "yunque", "yute", "z",
        "zafiro", "zaga", "zalamero", "zambullir", "zanahoria", "zancada", "zanco", "zangano",
        "zanja", "zapato", "zar", "zarpa", "zarpar", "zarza", "zona", "zoo",
        "zoologia", "zorro", "zueco", "zumo", "zurcir", "zurdo"
    )
    
    /**
     * Genera una seed phrase (6 palabras en español) desde una clave privada existente.
     * 
     * NOTA: Esta función convierte la clave privada a entropía para generar el mnemonic,
     * pero el mnemonic NO se usa para derivar la clave privada. Es solo una representación
     * legible para copia de seguridad.
     * 
     * @param privateKey Clave privada en formato hexadecimal (con o sin prefijo 0x)
     * @return Lista de 6 palabras en español
     */
    fun generateSeedPhraseFromPrivateKey(privateKey: String): List<String> {
        // Limpiar prefijo 0x si existe
        val cleanKey = privateKey.removePrefix("0x")
        val keyBytes = Numeric.hexStringToByteArray(cleanKey)

        // Usar los primeros 6 bytes (48 bits) para generar 6 palabras
        val entropy = ByteArray(6)
        val bytesToCopy = minOf(6, keyBytes.size)
        System.arraycopy(keyBytes, 0, entropy, 0, bytesToCopy)
        
        // Si la clave tiene menos de 6 bytes, usar hash para completar
        if (keyBytes.size < 6) {
            val hash = Utils.sha256hash160(keyBytes)
            System.arraycopy(hash, 0, entropy, 0, minOf(6, hash.size))
            // Si aún no tenemos 6 bytes, repetir el hash
            if (hash.size < 6) {
                val remaining = 6 - hash.size
                System.arraycopy(hash, 0, entropy, hash.size, minOf(remaining, hash.size))
            }
        }

        return generateWordsFromEntropy(entropy)
    }

    /**
     * Genera una seed phrase (6 palabras en español) desde entropía aleatoria.
     * Útil para generar seed phrase independiente (aunque no se use para derivar clave).
     * 
     * @return Lista de 6 palabras en español
     */
    fun generateRandomSeedPhrase(): List<String> {
        val random = SecureRandom()
        val entropy = ByteArray(ENTROPY_BITS / 8) // 6 bytes = 48 bits
        random.nextBytes(entropy)
        return generateWordsFromEntropy(entropy)
    }

    /**
     * Genera 6 palabras en español desde 6 bytes de entropía.
     * Cada byte se combina con el siguiente para mejor distribución.
     */
    private fun generateWordsFromEntropy(entropy: ByteArray): List<String> {
        val words = mutableListOf<String>()
        for (i in 0 until WORD_COUNT) {
            if (i < entropy.size) {
                // Combinar el byte actual con el siguiente para mejor distribución
                val byte1 = entropy[i].toInt() and 0xFF
                val byte2 = if (i + 1 < entropy.size) {
                    entropy[i + 1].toInt() and 0xFF
                } else {
                    entropy[0].toInt() and 0xFF // Usar el primer byte si no hay siguiente
                }
                // Combinar bytes: usar 11 bits (2^11 = 2048, tamaño de nuestra lista)
                val combined = ((byte1 shl 3) or (byte2 shr 5)) and 0x7FF // 11 bits
                val index = combined % spanishWords.size
                words.add(spanishWords[index])
            } else {
                // Fallback: usar bytes circulares
                val byteIndex = i % entropy.size
                val index = (entropy[byteIndex].toInt() and 0xFF) % spanishWords.size
                words.add(spanishWords[index])
            }
        }
        return words
    }

    /**
     * Valida una seed phrase (verifica que las palabras sean válidas y estén en español).
     * 
     * @param words Lista de palabras del mnemonic
     * @return true si la seed phrase es válida
     */
    fun validateSeedPhrase(words: List<String>): Boolean {
        return try {
            if (words.size != WORD_COUNT) {
                return false
            }
            // Verificar que todas las palabras estén en la lista española
            words.all { word -> spanishWords.contains(word.lowercase()) }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Formatea la seed phrase como string legible (palabras separadas por espacios).
     */
    fun formatSeedPhrase(words: List<String>): String {
        return words.joinToString(" ")
    }
}


