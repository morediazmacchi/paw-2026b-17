// Los 48 barrios oficiales de CABA. Lista fija, por eso es enum y no una tabla.
package ar.edu.itba.paw.model;

public enum Barrio {
    AGRONOMIA("Agronomía", -34.59294, -58.48867),
    ALMAGRO("Almagro", -34.60923, -58.42175),
    BALVANERA("Balvanera", -34.60910, -58.40306),
    BARRACAS("Barracas", -34.64640, -58.38427),
    BELGRANO("Belgrano", -34.55474, -58.45018),
    BOEDO("Boedo", -34.62996, -58.41884),
    CABALLITO("Caballito", -34.61682, -58.44360),
    CHACARITA("Chacarita", -34.58837, -58.45418),
    COGHLAN("Coghlan", -34.56062, -58.47494),
    COLEGIALES("Colegiales", -34.57464, -58.45097),
    CONSTITUCION("Constitución", -34.62504, -58.38439),
    FLORES("Flores", -34.63680, -58.45827),
    FLORESTA("Floresta", -34.62768, -58.48359),
    LA_BOCA("La Boca", -34.63107, -58.35682),
    LA_PATERNAL("La Paternal", -34.59742, -58.46867),
    LINIERS("Liniers", -34.64379, -58.51913),
    MATADEROS("Mataderos", -34.65837, -58.50174),
    MONTE_CASTRO("Monte Castro", -34.61930, -58.50658),
    MONSERRAT("Monserrat", -34.61268, -58.37965),
    NUEVA_POMPEYA("Nueva Pompeya", -34.65052, -58.41888),
    NUNEZ("Núñez", -34.54328, -58.46259),
    PALERMO("Palermo", -34.57385, -58.42234),
    PARQUE_AVELLANEDA("Parque Avellaneda", -34.64864, -58.47645),
    PARQUE_CHACABUCO("Parque Chacabuco", -34.63594, -58.43770),
    PARQUE_CHAS("Parque Chas", -34.58552, -58.47912),
    PARQUE_PATRICIOS("Parque Patricios", -34.63755, -58.40168),
    PUERTO_MADERO("Puerto Madero", -34.60904, -58.35572),
    RECOLETA("Recoleta", -34.58578, -58.39493),
    RETIRO("Retiro", -34.58840, -58.37600),
    SAAVEDRA("Saavedra", -34.55308, -58.48874),
    SAN_CRISTOBAL("San Cristóbal", -34.62386, -58.40189),
    SAN_NICOLAS("San Nicolás", -34.60367, -58.38052),
    SAN_TELMO("San Telmo", -34.62152, -58.37155),
    VELEZ_SARSFIELD("Vélez Sarsfield", -34.63136, -58.49328),
    VERSALLES("Versalles", -34.63012, -58.52241),
    VILLA_CRESPO("Villa Crespo", -34.59883, -58.44273),
    VILLA_DEL_PARQUE("Villa del Parque", -34.60425, -58.49068),
    VILLA_DEVOTO("Villa Devoto", -34.60235, -58.51428),
    VILLA_GENERAL_MITRE("Villa General Mitre", -34.61003, -58.46894),
    VILLA_LUGANO("Villa Lugano", -34.67499, -58.47617),
    VILLA_LURO("Villa Luro", -34.63641, -58.50273),
    VILLA_ORTUZAR("Villa Ortúzar", -34.58097, -58.46765),
    VILLA_PUEYRREDON("Villa Pueyrredón", -34.58210, -58.50350),
    VILLA_REAL("Villa Real", -34.61949, -58.52604),
    VILLA_RIACHUELO("Villa Riachuelo", -34.69186, -58.46335),
    VILLA_SANTA_RITA("Villa Santa Rita", -34.61619, -58.48296),
    VILLA_SOLDATI("Villa Soldati", -34.66542, -58.44658),
    VILLA_URQUIZA("Villa Urquiza", -34.57154, -58.48786);

    private static final double EARTH_RADIUS_KM = 6371.0;

    // Las coordenadas de cada barrio son el centroide de su poligono oficial (dataset "Barrios" de
    // Buenos Aires Data). Solo sirven para medir cercania entre barrios (ver distanceKmTo() y
    // RecommendationSql, que repite la misma formula en SQL), no para ubicar nada en el mapa.
    private final String displayName;
    private final double latitude;
    private final double longitude;

    Barrio(final String displayName, final double latitude, final double longitude) {
        this.displayName = displayName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    /** Distancia en km, en linea recta, entre los centroides de los dos barrios (0 si es el mismo). */
    public double distanceKmTo(final Barrio other) {
        if (this == other) {
            return 0;
        }
        final double lat1 = Math.toRadians(latitude);
        final double lat2 = Math.toRadians(other.latitude);
        final double dLat = lat2 - lat1;
        final double dLon = Math.toRadians(other.longitude - longitude);
        final double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /**
     * La mayor distancia entre dos barrios cualesquiera (~16,5 km): sirve para normalizar
     * "que tan lejos" a [0, 1]. Se calcula una vez; no hay que mantenerla a mano si se
     * corrige un centroide.
     */
    public static final double MAX_DISTANCE_KM = computeMaxDistanceKm();

    private static double computeMaxDistanceKm() {
        double max = 0;
        for (final Barrio a : values()) {
            for (final Barrio b : values()) {
                max = Math.max(max, a.distanceKmTo(b));
            }
        }
        return max;
    }
}
