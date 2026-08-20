package com.pepotech.pepoboveda.crypto

object Wordlist {

    private val CRUDA = """
        abeja abrigo abrir aceite acelga acento acero acido acoger acordar acta actor acudir adobe aduana
        afecto agenda agilidad agosto agrio agua aguja ahorro aire ajedrez ajeno ajo alambre alarma alba
        albura alcalde alce aldea alegre alerta aleta alfil alga algodon alma almeja altar alto alumno
        amable amanecer amapola ambar ambos amigo amor ampolla ancho ancla andar anillo animal anis
        antena antiguo anuncio anzuelo apagar aparato apellido apio aplauso apoyo apunte araña arbol arce
        archivo arcilla arco ardilla arena arepa arete argolla arista arma armario aroma arpa arreglo arroz
        arte asado ascenso aseo asfalto asiento asilo aspecto astro asunto atajo ataque atento atlas atun
        aula aumento aurora ausente autor avance ave avena avestruz avion aviso ayer ayuda azafran azar
        azucar azul babor bacalao bache bahia baile bajo balcon balde ballena balon bambu banco bandeja
        bandera banjo baño barba barco barniz barra barrio basalto base basura batalla bateria bazar bebida
        beca belleza bello berro besar betun biblia bicho bien bigote billete bingo bisonte bistec blanco
        bloque blusa bobina boca bodega bola boleto bolsa bomba bondad bonito borde bosque bota boton
        bovino boxeo boya bracero brasa bravo brazo brecha breve brida brillo brisa broca broche broma
        bronce brote bruma buceo bueno bufete buho bulto buque burbuja burro busca butaca buzon caballo
        cabaña cabeza cable cabra cacao cacto cadena cafe caida caja cajon calabaza calamar calcio caldo
        calle calma calor calvo cama cambio camello camino campo canal canela cangrejo canica canoa cansado
        cantar caña capa capaz capilla capitan capricho cara carbon carcel carga cariño carne carpeta carta
        casa cascada casco caseta casino caspa castillo catorce cauce caudal causa cautela cazar cebada
        cebolla cebra ceder cedro celda celeste cena cenar ceniza centro cepillo cera cerca cerdo cereza
        cero cerrar cesped cesta chal chaleco chapa charco chico chile chino chispa chiste choza cielo cien
        cierre cifra cigarra cima cinco cine cinta ciprer circo cirio ciruela cisne cita ciudad clarin
        claro clase clavel clavo clima cobre coche cocina codigo codo cofre coger cojin cola colcha colega
        colgar colina collar colmena colmo color columna comba combate comer comida cometa como compra
        comun concha conde conejo confiar conocer consejo copa copia coral corazon corbata corcho cordel
        cordero corneta corona correa correo cortar corto cosa cosecha coser costa costilla cotidiano coyote
        cráter crecer credo crema crespo criba crimen crin crisol cristal croqueta crudo cruz cuaderno
        cuadro cuarto cuatro cuba cubierta cubo cuchara cuchillo cuello cuenta cuerda cuerno cuerpo cuervo
        cuidar culebra culpa cultivo cumbre cuna cuota cupon cura curso curva custodia cuyo dado dama danza
        dardo dato deber debil decena decir dedal dedo defensa dejar delfin delgado delta demora dentro
        deporte derecho derrota desde deseo desnudo destino detalle deuda dia diadema diario dibujo dicha
        diente diesel dieta diez difuso digital dique disco diseño disfraz distinto diva divino doblez doce
        docena doctor dolar dolor domingo donar dorado dormir dosis dragon drama duda dueño dulce duna
        durazno duro ebano echar eclipse eco edad edicion edificio editor efecto eficaz ejemplo ejercito
        elefante elegir elenco elevar elipse elogio embudo emitir emocion empate empeño empresa enano
        encaje encanto encender encina enebro enero enfermo engaño enigma enjambre enlace enorme ensayo
        entero entrada envase enviar epoca equipo erizo error escala escoba escudo escuela esencia esfera
        esfuerzo espacio espada espejo espiga esponja esposa espuma esqui estaca estadio estanque estatua
        estilo estufa etapa etica etiqueta euro evento evitar exacto examen exceso exilio exito experto
        extra fabrica facil factor faena faisan faja falda fama familia fanal fango fardo farol fase favor
        fecha felino feliz feria fervor fibra ficha fideo fiebre fiel fiera fiesta figura fijar fila filete
        filtro final finca fino firma fisco flaco flauta flecha flor flota fluido foca fogata folio fondo
        forma foro forro fosa foto fragua frambuesa franja frasco frase fraude freir freno fresa frijol
        frio frito fruta fuego fuente fuerza fuga fumar funda furgon futbol futuro gafas gaita galeria
        galgo galleta gallo gamba ganar gancho ganso garaje garbanzo garfio garra garza gasa gasto gato
        gaviota gemelo general genio gente gesto gigante gimnasio girar giro globo gloria golfo golpe goma
        gorra gorrion gota gozar grabar gracia grado grama grande granero granizo grano grasa gremio grieta
        grifo gripe gris gritar grosella grueso grumo grupo guante guardia guerra guia guinda guisante
        guitarra gusano gustar haber habla hacer hacha hada halcon hamaca harina hazaña hebilla hebra
        hechizo helado helecho hembra herida hermano heroe herrero hervir hielo hierba hierro higado higo
        hijo hilo himno hincha hisopo hocico hogar hoja hola hollin hombre hondo honesto hongo honor hora
        horca horizonte hormiga horno hoyo hueco huella huerta hueso huevo humano humedad humilde humo
        hundir huron huso ibero idea ideal idilio idioma iglesia igual imagen iman imperio impulso inca
        incendio indice infante inicio insecto instante intento invierno iris isla jabali jabon jamon
        jardin jarra jaula jazmin jefe jengibre jinete jirafa jornada joven joya juego jueves juez jugar
        jugo juguete julio junco jungla junio juntar jurado justo juzgar kilo koala labio labor laca ladera
        ladron lago lamer lampara lana lancha langosta lanza lapiz largo lastima lata latido laurel lava
        lavar lazo leal leccion leche lechuga leer legumbre lejano lengua lenteja leon letra levantar leve
        ley leyenda liana libro licor lider liebre lienzo liga lima limite limon limpio lindo linea lino
        linterna liquido lirio lista litro llama llanto llanura llave llegar llenar llevar llorar lluvia
        lobo local locura lodo logro loma lomo lona loro losa lote loza lucero lucha lucir luego lugar
        lujo lumbre luna lunes lupa luto luz macho madera madre maduro maestro magia maiz maleta malla malo
        mamut manada mancha mandar manejar mango mani mano manta manzana mapa maqueta mar marca marco marea
        marfil margen marido marino marmol marron martes martillo marzo masa mascara mastil mata materia
        matiz matorral mayo mayor mazo mecha medalla media medida medio medusa mejilla mejor melena melon
        membrillo memoria menor mensaje menta mercado merluza mermelada mesa meseta meta metal metodo metro
        mezcla miedo miel miembro mierda miga mijo milagro militar mimo mina minero minuto mirar mirlo misa
        mismo mitad mito mixto mochila moda modelo modo mofeta mojar molde moler molino momento moneda monje
        mono monte mora morada morder moreno morsa mosca mosto motivo moto mover mozo mucho mudar mueble
        muela muelle muerte muestra mujer mula muleta multa mundo muñeca muralla museo musgo musica muslo
        nabo nacer nacion nada nadar nadie naipe naranja nariz narrar nata natal nave navidad neblina
        necesario nectar negar negocio negro nervio neto neutro nevada nicho nido niebla nieto nieve niño
        nivel noble noche nombre nopal norma norte nota noticia novato novela novio nube nuca nudillo nudo
        nuera nueve nuevo nuez numero nunca nutria oasis obeso obispo objeto obra obrero obtener oca ocaso
        oceano ochenta ocho ocio octavo octubre ocultar ocupar oda odio oeste ofensa oferta oficio ofrecer
        ogro oido oir ojo ola oleada oleo olfato oliva olla olmo olor olvido ombligo onda onza opaco opcion
        opera opinar opio orbita orca orden oreja organo orgullo origen orilla orina oro orquesta ortiga
        oruga osadia oscuro oso ostra otoño otro oveja ovillo ovni oxido oyente ozono pacto padre paella
        pagar pagina paisaje paja pajaro pala palabra palacio paleta palma palmera palo paloma pan panal
        pantalla panteon paño papa papel paquete par parado paraiso parcela pared pareja pariente parque
        parte pasa pasado pasar pasear paseo pasillo paso pasta pastel pata patata patio pato patron pauta
        pavo payaso paz peaje pecado pecera pecho pedal pedazo pedir pegar peine pelar peldaño pelea
        pelicula pelo pelota peluca pena pendiente pensar peña peon peor pepino pequeño pera percha perder
        perdiz perejil pereza perfil perico perla permiso perno perro persona pesa pesar pesca peso pestaña
        petalo petirrojo pez pezuña piano picar pico pichon pie piedra piel pierna pieza pijama pila
        pildora piloto pimienta pincel pino pintar pinza piña pipa piragua piramide pirata piso pista
        pistola pitar pizarra placa placer plan plancha planeta planta plata plato playa plaza plazo pleno
        pliegue plomo pluma pobre poco poder podio poema poeta polea policia pollo polo polvo pomada pomelo
        pomo poner popa porche poro portal posada poseer postal poste postre potaje potro pozo pradera
        precio pregunta premio prenda prensa presa prever primo prisa proa probar proceso proeza promesa
        pronto propio prosa prueba pua pueblo puente puerta puerto pues pulga pulir pulmon pulpo pulsera
        punta punto puño pupila pureza puro quedar queja quemar querer queso quicio quieto quijada quilate
        quimica quince quinto quiosco quitar quizas rabano rabia rabo racimo radar radio raiz raja rallar
        rama rampa rana rancho rango rapaz rapido rapto raqueta rasgo raspa rastro rata rato raton raya
        rayo raza razon real rebaño rebote receta rechazo recibo recio recodo recreo recto red redondo
        reflejo refran regalo regar regla regreso reina reino reir reja reloj remar remedio remo renacer
        rendija reno reparto repollo reptil rescate reseña reserva resina respeto resto retazo retiro retrato
        reunion revista rey rezar riego riel rienda riesgo rifa rincon riñon rio riqueza risa ritmo rito
        rival rizo robar roble robot roca rocio rodar rodilla roer rojo rollo romero rondar ropa rosa rosca
        rostro roto rotula rubi rubio rudo rueda rugir ruido ruina rumbo rumor rustico ruta rutina sabana
        saber sabio sable sabor sacar saco sagrado sal sala salchicha saldo salero salida salmo salmon
        salon salsa saltar salto salud salvar sanar sandia sangre sano santo sapo saque sardina sarten
        sastre satelite sauce savia saxofon secar seco secreto sector seda sede seguir seis sello selva
        semana semilla senda seno sentir seña señal señor sepia septimo sequia ser serie serio serpiente
        servir sesenta sesion seta setenta severo sidra siempre sierra siesta siete siglo signo sigue
        silbato silla simple sirena sitio sobre socio soda sofa sol solar soldado soler solido solo soltar
        sombra sombrero somos sonar sonido sonrisa soñar sopa soplar soporte sorbete sordo sorpresa sortija
        sospecha sostener sotano suave subir suceso sucio sudor suegro suela sueldo suelo sueño suerte
        suficiente sufrir sujeto sultan suma sumar suplir sur surco surgir susto sutil tabaco tabla taburete
        taco tacto tajada tala talco talla tallo talon tamaño tambor tanque tapa tapiz tarde tarea tarifa
        tarro tarta tasa tatuaje taza tazon teatro techo tecla tejado tejer tela telar tema temblor temer
        temor temporal tenaza tender tener tenis tenor tension tercio terco termino ternura terraza terreno
        tesoro testigo tetera texto tez tiara tibio tiburon tiempo tienda tierno tierra tieso tigre tijera
        tilde timbal timbre timon tinta tinte tio tipo tira tirar titan titulo tiza toalla tobillo tocar
        tocino todo toga toldo tomar tomate tomillo tono tonto topacio tope topo toque torax torcer torero
        tormenta torneo toro torpe torre torso torta tortuga tos tostada total trabajo tractor traer trafico
        trago traje trama trampa trance tranvia trapo tras trato trazo trebol trece treinta tren trenza tres
        triangulo tribu trigo trino tripa triste triunfo trofeo trombon trompa trono tropa trote trozo trucha
        truco trueno tubo tuerca tulipan tumba tumor tuna tunel turba turbina turismo turno tutor ubicar
        ubre ultimo umbral unico unidad unir universo uno untar uña urbano urgente urna usar usted utensilio
        util uva vaca vacio vacuna vagar vago vaina vajilla vale valija valle valor valse valvula vampiro
        vanidad vapor vaquero vara variar varilla vario varon vasija vaso vate vecino veinte vejez vela
        velero vello velo vena vencer venda vender veneno venir venta ventana venus ver verano verbo verdad
        verde verdura vereda verja verso verter vestido veterano vez via viaje vibora vicio victoria vida
        vidrio viejo viento viernes viga vigor villa vinagre vino viña violin virar virgen viruta visera
        visita visor vista vitral viuda vivaz vivir vivo volar volcan volumen volver vomito voto voz vuelo
        vuelta yacer yate yegua yema yerno yeso yodo yoga yogur yunque yute zafiro zafra zaguan zamarra
        zanahoria zanja zapato zarpa zarza zinc zocalo zona zorro zueco zumbido zumo zurdo
    """.trimIndent()

    val PALABRAS: List<String> = CRUDA
        .split(Regex("\\s+"))
        .map { it.trim().lowercase() }
        .filter { it.length in 4..8 && it.all { c -> c in 'a'..'z' } }
        .distinct()
        .sorted()

    val TAMANO: Int get() = PALABRAS.size
}
