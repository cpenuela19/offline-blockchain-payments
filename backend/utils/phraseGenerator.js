const crypto = require('crypto');

// Lista de 2048 palabras en español (sin tildes, sin ñ)
// Basada en BIP39 pero adaptada para español sin caracteres especiales
const SPANISH_WORDLIST = [
  'abajo', 'abrir', 'acero', 'acto', 'agua', 'aire', 'algo', 'alma', 'alto', 'amar',
  'amigo', 'ancho', 'andar', 'angel', 'animo', 'año', 'apoyo', 'arbol', 'arena', 'arte',
  'asado', 'asilo', 'atlas', 'audio', 'auto', 'avion', 'azul', 'bajo', 'banca', 'banco',
  'banda', 'barco', 'barro', 'base', 'bateria', 'bebida', 'bello', 'beso', 'bien', 'blanco',
  'boca', 'boda', 'bomba', 'borde', 'brazo', 'breve', 'brillo', 'bueno', 'bulto', 'burro',
  'cable', 'cabra', 'cacao', 'cada', 'cadena', 'caer', 'cafe', 'caja', 'caldo', 'calle',
  'calma', 'calor', 'cama', 'cambio', 'camino', 'campo', 'canal', 'canto', 'caña', 'capa',
  'capaz', 'carne', 'caro', 'carta', 'casa', 'caso', 'casta', 'causa', 'caza', 'cebra',
  'ceder', 'celda', 'cena', 'centro', 'cerco', 'cerdo', 'cerezo', 'cero', 'cerrar', 'cesar',
  'ciclo', 'ciego', 'cielo', 'cien', 'cierto', 'cifra', 'cima', 'cine', 'circo', 'ciruela',
  'cita', 'ciudad', 'claro', 'clase', 'clave', 'clima', 'cloro', 'club', 'cobre', 'coche',
  'codo', 'cofre', 'coger', 'cola', 'colgar', 'colina', 'color', 'comer', 'comida', 'comodo',
  'compa', 'comun', 'con', 'concha', 'condor', 'conejo', 'confiar', 'congo', 'cono', 'consejo',
  'contar', 'copa', 'copia', 'corazon', 'corcho', 'corder', 'coro', 'corona', 'correr', 'corte',
  'corto', 'cosa', 'coser', 'costa', 'crear', 'crecer', 'creer', 'crema', 'criar', 'crimen',
  'cristal', 'cruel', 'cruzar', 'cuadro', 'cuarto', 'cubano', 'cubrir', 'cuchara', 'cuello', 'cuenta',
  'cuerda', 'cuerpo', 'cueva', 'cuidar', 'culebra', 'culpa', 'culto', 'cumbre', 'cuna', 'cuota',
  'curar', 'curso', 'curva', 'dado', 'dama', 'danza', 'dar', 'dato', 'deber', 'debil',
  'decir', 'dedo', 'defender', 'dejar', 'delgado', 'demas', 'derecho', 'derrota', 'desde', 'desear',
  'desierto', 'despues', 'destino', 'detalle', 'deuda', 'dia', 'diablo', 'diamante', 'dibujo', 'dicho',
  'diente', 'dieta', 'diez', 'dificil', 'dinero', 'dios', 'diploma', 'direccion', 'dirigir', 'disco',
  'disfraz', 'disparo', 'distante', 'divertir', 'doblar', 'doble', 'doce', 'doctor', 'dolor', 'domingo',
  'donar', 'donde', 'dormir', 'dorso', 'dos', 'drama', 'droga', 'ducha', 'duda', 'dueño',
  'dulce', 'durar', 'duro', 'ebrio', 'echar', 'edad', 'edificio', 'editar', 'educar', 'efecto',
  'ego', 'ejemplo', 'ejercito', 'el', 'elegir', 'elevar', 'elite', 'ella', 'ello', 'embargo',
  'empezar', 'empleo', 'empresa', 'en', 'enano', 'encima', 'encontrar', 'energia', 'enfermo', 'engaño',
  'enigma', 'enlace', 'enojo', 'enorme', 'ensayo', 'entero', 'entrada', 'entrar', 'entre', 'entregar',
  'envio', 'epoca', 'equipo', 'error', 'escala', 'escaso', 'escena', 'escribir', 'escudo', 'escuela',
  'esfuerzo', 'eslabon', 'espejo', 'esperar', 'espia', 'esposa', 'espuma', 'esquina', 'esta', 'estado',
  'estanque', 'estar', 'este', 'estilo', 'estimar', 'estomago', 'estrella', 'estreno', 'estudiar', 'etapa',
  'eterno', 'etica', 'europa', 'evadir', 'evento', 'evidencia', 'evitar', 'exacto', 'examen', 'exceso',
  'exigir', 'exilio', 'exito', 'explicar', 'exponer', 'extrano', 'extraño', 'fabrica', 'fabuloso', 'facil',
  'factor', 'faja', 'falda', 'fallar', 'falso', 'faltar', 'fama', 'familia', 'famoso', 'fango',
  'fantasia', 'faro', 'fase', 'fastidio', 'fatal', 'favor', 'fecha', 'feliz', 'feo', 'feria',
  'feroz', 'fertil', 'festejar', 'fiebre', 'fiel', 'fiera', 'fiesta', 'figura', 'fijar', 'fila',
  'filo', 'filtro', 'fin', 'final', 'finca', 'fingir', 'fino', 'firma', 'firmar', 'fisico',
  'flaco', 'flauta', 'flecha', 'flor', 'flota', 'fluir', 'foco', 'fogata', 'fondo', 'forma',
  'formar', 'foro', 'fortuna', 'forzar', 'foto', 'fracaso', 'fragil', 'franco', 'frasco', 'freno',
  'frente', 'fresco', 'frio', 'frontera', 'fruta', 'fuego', 'fuente', 'fuera', 'fuerte', 'fuga',
  'fumar', 'funcion', 'funda', 'furia', 'futuro', 'gafas', 'gaita', 'gajo', 'galeria', 'galgo',
  'gallo', 'gama', 'ganar', 'gancho', 'ganso', 'garaje', 'garantia', 'garbanzo', 'garganta', 'garra',
  'garza', 'gas', 'gastar', 'gato', 'gaviota', 'gemelo', 'gemir', 'genio', 'gente', 'gerente',
  'germen', 'gesto', 'gigante', 'gimnasio', 'girar', 'globo', 'gloria', 'glosa', 'gol', 'golpe',
  'goma', 'gordo', 'gorila', 'gorra', 'gota', 'goteo', 'gozar', 'grabar', 'gracia', 'grado',
  'gramo', 'gran', 'grande', 'grano', 'grasa', 'gratis', 'grave', 'grieta', 'grillo', 'gris',
  'grito', 'grueso', 'grupo', 'guante', 'guapo', 'guardar', 'guerra', 'guia', 'guijarro', 'guion',
  'gusano', 'gustar', 'gusto', 'haber', 'habito', 'habla', 'hablar', 'hacer', 'hacha', 'hada',
  'hallar', 'hamaca', 'harina', 'harto', 'hasta', 'hay', 'hazaña', 'hecho', 'helado', 'helar',
  'helecho', 'heno', 'herida', 'herir', 'hermano', 'heroe', 'hervir', 'hielo', 'hierba', 'hierro',
  'higo', 'hijo', 'hilo', 'himno', 'hipo', 'historia', 'hocico', 'hogar', 'hoja', 'hombre',
  'hombro', 'hondo', 'honor', 'hora', 'hormiga', 'horno', 'hoyo', 'hoy', 'hueco', 'huevo',
  'huir', 'humano', 'humedad', 'humilde', 'humo', 'hundir', 'huracan', 'ida', 'ideal', 'idear',
  'idioma', 'idiota', 'idolo', 'iglesia', 'igual', 'ilegal', 'ilusion', 'imagen', 'imitar', 'impar',
  'imperio', 'imponer', 'importar', 'impulso', 'inca', 'incapaz', 'incendio', 'incierto', 'incluir', 'incluso',
  'indice', 'indio', 'individuo', 'industria', 'inevitable', 'infancia', 'infantil', 'infeliz', 'inferior', 'infiel',
  'infinito', 'inflar', 'influir', 'informar', 'ingenio', 'ingenuo', 'ingresar', 'iniciar', 'inicio', 'injusto',
  'inmenso', 'inmune', 'innato', 'inocente', 'insecto', 'insistir', 'instante', 'instar', 'instinto', 'instruir',
  'insulto', 'intacto', 'intelecto', 'inteligente', 'intentar', 'interes', 'interior', 'interno', 'intimo', 'intriga',
  'introducir', 'inutil', 'inventar', 'inversion', 'invertir', 'invitar', 'ir', 'ira', 'ironia', 'isla',
  'izquierda', 'jabon', 'jade', 'jamas', 'jardin', 'jarra', 'jaula', 'jefe', 'jerga', 'jersey',
  'joven', 'joya', 'juego', 'jueves', 'juez', 'jugador', 'jugar', 'jugo', 'juicio', 'julio',
  'junio', 'juntar', 'junto', 'jurado', 'jurar', 'justicia', 'justo', 'juzgar', 'kilo', 'labio',
  'labor', 'labrador', 'lado', 'ladrillo', 'ladron', 'lagarto', 'lago', 'lamentar', 'lamer', 'lampara',
  'lana', 'lancha', 'lanzar', 'lapiz', 'largo', 'lata', 'lateral', 'latido', 'latigo', 'latir',
  'latin', 'lazo', 'leal', 'leccion', 'leche', 'lecho', 'leer', 'legado', 'legal', 'legar',
  'legion', 'legitimo', 'lego', 'lejos', 'lengua', 'lento', 'leon', 'leopardo', 'lesion', 'letal',
  'letra', 'levantar', 'ley', 'leyenda', 'liar', 'liberal', 'libertad', 'libre', 'libro', 'licencia',
  'lider', 'lienzo', 'liga', 'ligar', 'ligero', 'lima', 'limite', 'limon', 'limpiar', 'limpio',
  'linea', 'linterna', 'liquido', 'lista', 'listo', 'litera', 'litro', 'lobo', 'local', 'locura',
  'lodo', 'logica', 'lograr', 'lomo', 'lona', 'longitud', 'loro', 'lote', 'lucha', 'lucir',
  'lugar', 'lujo', 'luna', 'lunes', 'luz', 'llama', 'llamar', 'llano', 'llanta', 'llanto',
  'llave', 'llegar', 'llenar', 'lleno', 'llevar', 'llorar', 'llover', 'lluvia', 'lobo', 'local',
  'locura', 'logica', 'lograr', 'lomo', 'lona', 'longitud', 'loro', 'lote', 'lucha', 'lucir',
  'lugar', 'lujo', 'luna', 'lunes', 'luz', 'maceta', 'macho', 'madera', 'madre', 'madrigal',
  'maduro', 'maestro', 'magia', 'magnate', 'magnifico', 'mago', 'maiz', 'majestad', 'mal', 'maleta',
  'malo', 'mama', 'mamut', 'mancha', 'mandar', 'mandato', 'manejar', 'manga', 'mano', 'manso',
  'manta', 'mantel', 'mantequilla', 'manual', 'manzana', 'mapa', 'maquillaje', 'maquina', 'mar', 'maravilla',
  'marca', 'marcha', 'marchitar', 'marea', 'mareo', 'margen', 'marido', 'marino', 'marmol', 'marron',
  'martes', 'martillo', 'mas', 'masa', 'masaje', 'mascar', 'mascara', 'masculino', 'matar', 'mate',
  'material', 'matriz', 'maya', 'mayo', 'mayor', 'maza', 'mecha', 'medalla', 'mediano', 'medicina',
  'medida', 'medio', 'medir', 'mejor', 'melodia', 'membrana', 'memoria', 'menor', 'menos', 'mensaje',
  'mente', 'mentir', 'mentira', 'menu', 'mercado', 'merced', 'merecer', 'merienda', 'merito', 'mes',
  'mesa', 'metal', 'meter', 'metodo', 'metro', 'mezcla', 'mezclar', 'miedo', 'miel', 'miembro',
  'mientras', 'miga', 'migrar', 'mil', 'militar', 'millon', 'mina', 'mineral', 'minimo', 'ministerio',
  'minuto', 'mio', 'mirada', 'mirar', 'misa', 'mismo', 'mitad', 'mito', 'mochila', 'moda',
  'modelo', 'moderar', 'modo', 'mojar', 'molde', 'moler', 'molestar', 'molino', 'momento', 'moneda',
  'mono', 'monstruo', 'montaña', 'monte', 'morado', 'moral', 'morar', 'morder', 'moreno', 'morir',
  'moro', 'mortal', 'mosca', 'mosquito', 'mostrar', 'moto', 'motor', 'mover', 'movil', 'mozo',
  'mucho', 'mudar', 'mueble', 'muela', 'muerte', 'muerto', 'muestra', 'mujer', 'mula', 'multa',
  'mundo', 'mural', 'muro', 'musa', 'musculo', 'musica', 'musico', 'mutar', 'mutuo', 'nacer',
  'nacion', 'nada', 'nadar', 'nadie', 'naipe', 'naranja', 'nariz', 'narracion', 'narrar', 'nata',
  'nativo', 'natural', 'naturaleza', 'naufragio', 'nave', 'navegar', 'necesario', 'necesitar', 'necio', 'negar',
  'negocio', 'negro', 'negro', 'nervio', 'nervioso', 'neto', 'neutro', 'nevar', 'nevera', 'ni',
  'niebla', 'nieve', 'nieto', 'nieve', 'nino', 'nivel', 'noble', 'noche', 'nodo', 'nogal',
  'nombre', 'normal', 'norte', 'nosotros', 'nota', 'notar', 'noticia', 'novato', 'novela', 'noviembre',
  'novio', 'nube', 'nublado', 'nuca', 'nucleo', 'nudillo', 'nudo', 'nueve', 'nuevo', 'nuez',
  'numero', 'numeroso', 'nunca', 'nutria', 'oasis', 'obedecer', 'objeto', 'obligar', 'obra', 'obrero',
  'observar', 'obstaculo', 'obtener', 'obvio', 'ocasion', 'ocaso', 'oceano', 'ochenta', 'ocho', 'ocio',
  'octubre', 'ocular', 'ocultar', 'ocupar', 'ocurrir', 'odiar', 'odio', 'ofender', 'oferta', 'oficial',
  'oficio', 'ofrecer', 'oido', 'oir', 'ojal', 'ojo', 'ola', 'oleada', 'olfato', 'oliva',
  'olivo', 'olmo', 'olor', 'olvidar', 'ombligo', 'onda', 'onza', 'opaco', 'operar', 'opinion',
  'oponer', 'oportuno', 'oprimir', 'optar', 'optimista', 'opuesto', 'oracion', 'orador', 'oral', 'orden',
  'ordenar', 'oreja', 'organo', 'orgullo', 'origen', 'orilla', 'oro', 'orquesta', 'ortiga', 'osar',
  'oscuro', 'oso', 'ostra', 'otoño', 'otro', 'oveja', 'oxido', 'oxigeno', 'oye', 'oyente',
  'pabellon', 'pacto', 'padecer', 'padre', 'paella', 'pagar', 'pago', 'pais', 'paja', 'pajaro',
  'pala', 'palabra', 'palacio', 'palco', 'paleta', 'palma', 'palmera', 'palo', 'paloma', 'palpar',
  'pan', 'panal', 'panico', 'pantalon', 'pantera', 'pantalla', 'pantano', 'panuelo', 'papa', 'papel',
  'paquete', 'par', 'para', 'parada', 'parado', 'paraiso', 'parar', 'parcela', 'pared', 'pareja',
  'parejo', 'parentesis', 'pariente', 'parir', 'paro', 'parque', 'parra', 'parte', 'partido', 'partir',
  'pasa', 'pasado', 'pasaje', 'pasar', 'pasear', 'pasillo', 'paso', 'pasta', 'pastel', 'pastor',
  'pata', 'patada', 'patear', 'patente', 'paterno', 'patio', 'pato', 'patria', 'patron', 'pausa',
  'pauta', 'pavo', 'paz', 'peaje', 'peaton', 'pecado', 'pecar', 'pecho', 'pedal', 'pedazo',
  'pedir', 'pegar', 'peine', 'pelar', 'pelea', 'pelicula', 'pelo', 'pelota', 'pena', 'pensar',
  'peña', 'peor', 'pequeño', 'pera', 'perder', 'perdido', 'perdon', 'perdurar', 'peregrino', 'perezoso',
  'perfeccion', 'perfil', 'perforar', 'perfume', 'periodo', 'perla', 'permanecer', 'permiso', 'pero', 'perro',
  'perseguir', 'persona', 'pertenecer', 'pesado', 'pesar', 'pesca', 'pescar', 'peso', 'pestana', 'petalo',
  'pez', 'piano', 'picar', 'pie', 'piedra', 'piel', 'pierna', 'pieza', 'pila', 'piloto',
  'pimienta', 'pino', 'pintar', 'pintor', 'pinza', 'piojo', 'pipa', 'pirata', 'pisar', 'piso',
  'pista', 'pistola', 'pizarra', 'placa', 'placer', 'plan', 'plancha', 'planeta', 'plano', 'planta',
  'plantar', 'plasma', 'plastico', 'plata', 'plato', 'playa', 'plaza', 'plazo', 'plegar', 'pleno',
  'pliego', 'plomo', 'pluma', 'plural', 'poblacion', 'pobre', 'poco', 'poder', 'podio', 'poema',
  'poesia', 'poeta', 'polen', 'policia', 'polilla', 'politica', 'pollo', 'polo', 'polvo', 'pomada',
  'pomo', 'poner', 'poniente', 'popa', 'popular', 'por', 'porcion', 'porque', 'portal', 'portar',
  'portero', 'porto', 'posada', 'posar', 'poseer', 'posible', 'poste', 'postre', 'potencia', 'potente',
  'practica', 'practicar', 'prado', 'precio', 'preciso', 'predicar', 'preferir', 'pregunta', 'preguntar', 'premio',
  'prenda', 'preparar', 'presencia', 'presentar', 'presente', 'presidente', 'presion', 'preso', 'prestar', 'pretender',
  'pretexto', 'prevenir', 'prima', 'primavera', 'primero', 'primo', 'princesa', 'principal', 'principio', 'prisa',
  'privado', 'probar', 'problema', 'proceder', 'proceso', 'procurar', 'producir', 'producto', 'profano', 'profesor',
  'profeta', 'profundo', 'programa', 'progreso', 'prohibir', 'promesa', 'prometer', 'promover', 'pronto', 'pronunciar',
  'propio', 'proponer', 'prosa', 'proteccion', 'proteger', 'protesta', 'protestar', 'provecho', 'proveer', 'provincia',
  'proximo', 'proyecto', 'prueba', 'publico', 'pueblo', 'puente', 'puerta', 'puerto', 'pues', 'puesto',
  'pulga', 'pulgar', 'pulir', 'pulmon', 'pulpo', 'pulso', 'punta', 'puntal', 'puntapié', 'punto',
  'punzon', 'pupila', 'pureza', 'purificar', 'puro', 'puso', 'quebrar', 'quedar', 'queja', 'quemar',
  'querer', 'queso', 'quien', 'quieto', 'quimica', 'quince', 'quitar', 'rabia', 'rabo', 'racha',
  'racimo', 'radical', 'radio', 'raiz', 'rama', 'ramo', 'rana', 'rango', 'rapido', 'rapto',
  'raro', 'rasgar', 'rasgo', 'rastro', 'rata', 'rato', 'raton', 'raya', 'rayo', 'raza',
  'razon', 'reaccion', 'real', 'realidad', 'realizar', 'rebanada', 'rebote', 'recaer', 'receta', 'rechazar',
  'recibir', 'recibo', 'recinto', 'recitar', 'reclamar', 'reclamo', 'recoger', 'recomendar', 'reconocer', 'recordar',
  'recorrer', 'recorte', 'recreo', 'recto', 'recuerdo', 'recurso', 'red', 'redactar', 'redimir', 'reducir',
  'reemplazar', 'referir', 'reflejar', 'reflejo', 'reforma', 'refrescar', 'refugio', 'regalo', 'regar', 'regimen',
  'region', 'regir', 'registro', 'regla', 'regresar', 'regular', 'reina', 'reino', 'reir', 'reja',
  'relacion', 'relajar', 'relampago', 'relatar', 'relato', 'relevante', 'relieve', 'religion', 'reloj', 'remar',
  'remedio', 'remitir', 'remo', 'remolino', 'remontar', 'remover', 'rencor', 'rendir', 'renegar', 'renovar',
  'renta', 'repartir', 'repente', 'repetir', 'replicar', 'reporte', 'reposo', 'representar', 'reproducir', 'reptil',
  'repuesto', 'requerir', 'res', 'rescatar', 'resena', 'reserva', 'resfriado', 'residir', 'resistir', 'resolver',
  'resonar', 'resorte', 'respecto', 'respirar', 'respiro', 'responder', 'responsable', 'resta', 'restar', 'resto',
  'resultado', 'resumen', 'retar', 'retener', 'retirar', 'retiro', 'retorcer', 'retornar', 'retrato', 'retroceso',
  'reunion', 'reunir', 'revelar', 'reventar', 'reverencia', 'reverso', 'revisar', 'revista', 'revolver', 'rey',
  'rezar', 'riachuelo', 'ribera', 'rico', 'ridiculo', 'rienda', 'riesgo', 'rifa', 'rigido', 'rigor',
  'rima', 'rimar', 'rincón', 'ring', 'rio', 'riqueza', 'risa', 'ritmo', 'rito', 'rival',
  'rizar', 'robar', 'roble', 'robo', 'robot', 'roca', 'roce', 'rocio', 'rodar', 'rodear',
  'rodilla', 'roer', 'rogar', 'rojo', 'rol', 'rollo', 'romano', 'romper', 'ron', 'roncar',
  'ronda', 'ropa', 'ropero', 'rosa', 'rosado', 'rostro', 'rotar', 'roto', 'rotura', 'rubi',
  'rubio', 'rudo', 'rueda', 'ruedo', 'ruego', 'ruido', 'ruina', 'ruiseñor', 'rumbo', 'rumor',
  'rural', 'ruso', 'rustico', 'ruta', 'saber', 'sabio', 'sabor', 'sabroso', 'sacar', 'sacerdote',
  'saco', 'sagrado', 'sala', 'salado', 'salario', 'salchicha', 'saldo', 'salida', 'salir', 'saliva',
  'salmon', 'salon', 'salpicar', 'saltar', 'salto', 'salud', 'saludar', 'saludo', 'salvaje', 'salvar',
  'salvo', 'sangre', 'sanidad', 'sano', 'santo', 'sapo', 'saque', 'sardina', 'sarten', 'sastre',
  'satelite', 'satisfacer', 'sauce', 'savia', 'saxofon', 'seccion', 'seco', 'secretaria', 'secreto', 'sector',
  'secuencia', 'secundario', 'sed', 'seda', 'sede', 'sediento', 'seguir', 'segundo', 'seguridad', 'seguro',
  'seis', 'seleccion', 'sello', 'selva', 'semana', 'semilla', 'sencillo', 'seno', 'sensacion', 'sentar',
  'sentido', 'sentimiento', 'sentir', 'señal', 'señor', 'separar', 'septiembre', 'ser', 'sereno', 'serie',
  'serio', 'sermon', 'serpiente', 'servicio', 'servidor', 'servir', 'sesion', 'seta', 'setenta', 'severo',
  'sexo', 'sexto', 'si', 'sido', 'siembra', 'siempre', 'sien', 'sierra', 'siesta', 'siete',
  'siglo', 'significar', 'signo', 'siguiente', 'silaba', 'silbar', 'silbido', 'silencio', 'silencioso', 'silla',
  'sillon', 'silo', 'simbologia', 'simbolo', 'simetria', 'simil', 'simpatia', 'simple', 'simular', 'simultaneo',
  'sin', 'sincero', 'sindicato', 'singular', 'siniestro', 'sino', 'sintesis', 'sintoma', 'sinuoso', 'sirena',
  'sistema', 'sitio', 'situacion', 'situar', 'soberano', 'sobra', 'sobrar', 'sobre', 'sobrevivir', 'sobrio',
  'sociedad', 'socio', 'socorro', 'sofocar', 'soga', 'sol', 'solapa', 'solar', 'soldado', 'soldar',
  'soleado', 'soler', 'solicitar', 'solidaridad', 'solido', 'solitario', 'solo', 'soltar', 'solucion', 'solucionar',
  'sombra', 'sombrero', 'sombrilla', 'sonar', 'sonido', 'sonreir', 'sonrisa', 'sonrojar', 'sopa', 'soplar',
  'soplo', 'soportar', 'sorbo', 'sordo', 'sorprender', 'sorpresa', 'sortear', 'sospecha', 'sospechar', 'sostener',
  'sotano', 'suave', 'subir', 'subito', 'submarino', 'subrayar', 'suceder', 'suceso', 'sucursal', 'sudar',
  'sudor', 'suegra', 'suegro', 'suelo', 'suelta', 'suelto', 'sueño', 'suerte', 'sufrir', 'sugerir',
  'suicidio', 'sujetar', 'sujeto', 'sultán', 'suma', 'sumar', 'sumergir', 'suministro', 'sumo', 'superar',
  'superficie', 'superior', 'supermercado', 'suplemento', 'suplicar', 'suponer', 'supremo', 'sur', 'surgir', 'surrealista',
  'surtido', 'susceptible', 'suspender', 'suspenso', 'sustancia', 'sustentar', 'sustituir', 'sustraer', 'susurrar', 'sutil',
  'tabaco', 'tabla', 'tablero', 'tacita', 'tacto', 'tajo', 'tal', 'tala', 'talento', 'talla',
  'taller', 'tallo', 'tamano', 'tambien', 'tambor', 'tampoco', 'tan', 'tanda', 'tango', 'tanque',
  'tanto', 'tapa', 'tapete', 'tapizar', 'tapon', 'taquilla', 'tardar', 'tarde', 'tarea', 'tarifa',
  'tarjeta', 'tarro', 'tarta', 'tartamudear', 'tasacion', 'tasa', 'tasar', 'tatuaje', 'tauro', 'taxi',
  'taza', 'te', 'teatro', 'techo', 'tecla', 'teclado', 'tecnica', 'tecnico', 'tecnologia', 'teja',
  'tejer', 'tejido', 'tela', 'telefono', 'telescopio', 'televisor', 'telon', 'tema', 'temblar', 'temer',
  'temperatura', 'templado', 'templo', 'temporada', 'temporal', 'temprano', 'tenaz', 'tender', 'tendero', 'tener',
  'tenis', 'tension', 'tentar', 'teoria', 'tercero', 'tercio', 'terminar', 'termino', 'termostato', 'ternura',
  'terreno', 'terrestre', 'terrible', 'terror', 'tesoro', 'testigo', 'texto', 'textura', 'tiempo', 'tienda',
  'tierra', 'tieso', 'tigre', 'tilde', 'timbre', 'timido', 'timo', 'timon', 'tina', 'tinta',
  'tinte', 'tio', 'tipo', 'tira', 'tirar', 'tiro', 'titan', 'titulo', 'tiza', 'toalla',
  'tobillo', 'tocar', 'tocino', 'todo', 'toldo', 'tolerar', 'tomar', 'tomate', 'tonelada', 'tono',
  'tonteria', 'tonto', 'topar', 'toque', 'torbellino', 'torcer', 'tornado', 'torno', 'toro', 'torpe',
  'torre', 'tortuga', 'tos', 'toser', 'total', 'trabajar', 'trabajo', 'traccion', 'tractor', 'traducir',
  'traer', 'trafico', 'tragar', 'trago', 'traicion', 'traje', 'tramo', 'trampa', 'tranquilo', 'transaccion',
  'transbordar', 'transferir', 'transformar', 'transicion', 'transito', 'transmitir', 'transparente', 'transportar', 'trapo', 'tras',
  'trascender', 'trasero', 'trasladar', 'traspasar', 'traste', 'tratado', 'tratar', 'travesia', 'travieso', 'trayectoria',
  'trazar', 'trece', 'tregua', 'treinta', 'tren', 'trenza', 'trepar', 'tres', 'tribu', 'tribunal',
  'tributo', 'trigo', 'trillar', 'trimestre', 'trinchera', 'triple', 'triste', 'triturar', 'triunfo', 'trofeo',
  'trompeta', 'tronco', 'trono', 'tropa', 'tropico', 'trozo', 'truco', 'trueno', 'trufa', 'tu',
  'tuberia', 'tubo', 'tucan', 'tumba', 'tumbar', 'tumor', 'tunel', 'turba', 'turismo', 'turno',
  'turquesa', 'tutela', 'tutor', 'ubicar', 'ultimo', 'umbral', 'un', 'una', 'unico', 'unidad',
  'unificar', 'union', 'unir', 'universal', 'universo', 'uno', 'untar', 'uña', 'urbano', 'urbe',
  'urgente', 'urna', 'usar', 'uso', 'usted', 'util', 'utilidad', 'utilizar', 'utopia', 'uva',
  'vaca', 'vacacion', 'vaciar', 'vacio', 'vacuna', 'vagar', 'vago', 'vaina', 'vajilla', 'vale',
  'valer', 'valiente', 'valle', 'valor', 'valvula', 'vampiro', 'vapor', 'vara', 'variar', 'varilla',
  'vario', 'vaso', 'vasto', 'vaya', 'vecino', 'vector', 'vehiculo', 'veinte', 'vejez', 'vela',
  'velar', 'velocidad', 'vena', 'venado', 'vender', 'veneno', 'venerar', 'vengador', 'venganza', 'venir',
  'venta', 'ventaja', 'ventana', 'ventilador', 'ventura', 'ver', 'verano', 'verbo', 'verdad', 'verde',
  'vereda', 'verificar', 'verja', 'verso', 'verter', 'vertical', 'vespertino', 'vestido', 'vestir', 'veterano',
  'veto', 'vez', 'via', 'viajar', 'viaje', 'viajero', 'vibora', 'vibrar', 'vicio', 'victima',
  'victoria', 'vid', 'vida', 'video', 'vidrio', 'viejo', 'viento', 'vientre', 'viernes', 'vigente',
  'vigilar', 'vigor', 'vil', 'villa', 'vinagre', 'vincular', 'vino', 'viola', 'violar', 'violencia',
  'violento', 'violin', 'violeta', 'virgen', 'viril', 'virtual', 'virtud', 'virus', 'visa', 'visera',
  'visible', 'vision', 'visita', 'visitar', 'vista', 'vital', 'vitamina', 'vitoria', 'viuda', 'vivir',
  'vivo', 'vocablo', 'vocacion', 'vocal', 'volador', 'volar', 'volcan', 'volumen', 'voluntad', 'volver',
  'vomitar', 'votar', 'voto', 'voz', 'vuelo', 'vuelta', 'vulgar', 'vulnerable', 'y', 'ya',
  'yacer', 'yate', 'yegua', 'yema', 'yerno', 'yeso', 'yo', 'yoga', 'yogur', 'yunque',
  'yuyo', 'zafiro', 'zaga', 'zalamero', 'zancada', 'zanco', 'zanja', 'zapato', 'zarpa', 'zarpar',
  'zarza', 'zona', 'zonzo', 'zorro', 'zueco', 'zumo', 'zurcir', 'zurdo'
];

/**
 * Normaliza una palabra: elimina tildes y caracteres especiales del español
 * @param {string} word - Palabra a normalizar
 * @returns {string} - Palabra normalizada
 */
function normalizeWord(word) {
  return String(word)
    .trim()
    .toLowerCase()
    .normalize('NFD') // Descompone caracteres con tildes
    .replace(/[\u0300-\u036f]/g, '') // Elimina diacríticos (tildes)
    .replace(/ñ/g, 'n') // Reemplaza ñ por n
    .replace(/[^a-z0-9]/g, ''); // Elimina cualquier otro carácter no alfanumérico
}

/**
 * Genera una frase de 10 palabras aleatorias en español (sin tildes, sin ñ)
 * Las palabras se normalizan antes de devolverlas para asegurar consistencia
 * @returns {string[]} - Array de 10 palabras normalizadas
 */
function generatePhrase10() {
  const words = [];
  for (let i = 0; i < 10; i++) {
    const randomIndex = crypto.randomInt(0, SPANISH_WORDLIST.length);
    const word = SPANISH_WORDLIST[randomIndex];
    // Normalizar la palabra antes de agregarla (elimina tildes y caracteres especiales)
    words.push(normalizeWord(word));
  }
  return words;
}

/**
 * Normaliza una frase de 10 palabras: minúsculas, trim, elimina tildes y caracteres especiales
 * @param {string[]} phrase10 - Array de palabras
 * @returns {string} - Frase normalizada como string
 */
function normalizePhrase(phrase10) {
  if (!Array.isArray(phrase10) || phrase10.length !== 10) {
    throw new Error('phrase10 debe ser un array de 10 palabras');
  }
  
  return phrase10
    .map(word => normalizeWord(word))
    .filter(word => word.length > 0)
    .join(' ');
}

/**
 * Calcula el hash SHA-256 de una frase normalizada
 * @param {string[]} phrase10 - Array de palabras
 * @returns {string} - Hash SHA-256 en formato hex
 */
function hashPhrase(phrase10) {
  const normalized = normalizePhrase(phrase10);
  return crypto.createHash('sha256').update(normalized).digest('hex');
}

module.exports = {
  generatePhrase10,
  normalizePhrase,
  hashPhrase
};

