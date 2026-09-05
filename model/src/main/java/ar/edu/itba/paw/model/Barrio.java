// Los 48 barrios oficiales de CABA. Lista fija, por eso es enum y no una tabla.
package ar.edu.itba.paw.model;

public enum Barrio {
    AGRONOMIA("Agronomía"),
    ALMAGRO("Almagro"),
    BALVANERA("Balvanera"),
    BARRACAS("Barracas"),
    BELGRANO("Belgrano"),
    BOEDO("Boedo"),
    CABALLITO("Caballito"),
    CHACARITA("Chacarita"),
    COGHLAN("Coghlan"),
    COLEGIALES("Colegiales"),
    CONSTITUCION("Constitución"),
    FLORES("Flores"),
    FLORESTA("Floresta"),
    LA_BOCA("La Boca"),
    LA_PATERNAL("La Paternal"),
    LINIERS("Liniers"),
    MATADEROS("Mataderos"),
    MONTE_CASTRO("Monte Castro"),
    MONSERRAT("Monserrat"),
    NUEVA_POMPEYA("Nueva Pompeya"),
    NUNEZ("Núñez"),
    PALERMO("Palermo"),
    PARQUE_AVELLANEDA("Parque Avellaneda"),
    PARQUE_CHACABUCO("Parque Chacabuco"),
    PARQUE_CHAS("Parque Chas"),
    PARQUE_PATRICIOS("Parque Patricios"),
    PUERTO_MADERO("Puerto Madero"),
    RECOLETA("Recoleta"),
    RETIRO("Retiro"),
    SAAVEDRA("Saavedra"),
    SAN_CRISTOBAL("San Cristóbal"),
    SAN_NICOLAS("San Nicolás"),
    SAN_TELMO("San Telmo"),
    VELEZ_SARSFIELD("Vélez Sarsfield"),
    VERSALLES("Versalles"),
    VILLA_CRESPO("Villa Crespo"),
    VILLA_DEL_PARQUE("Villa del Parque"),
    VILLA_DEVOTO("Villa Devoto"),
    VILLA_GENERAL_MITRE("Villa General Mitre"),
    VILLA_LUGANO("Villa Lugano"),
    VILLA_LURO("Villa Luro"),
    VILLA_ORTUZAR("Villa Ortúzar"),
    VILLA_PUEYRREDON("Villa Pueyrredón"),
    VILLA_REAL("Villa Real"),
    VILLA_RIACHUELO("Villa Riachuelo"),
    VILLA_SANTA_RITA("Villa Santa Rita"),
    VILLA_SOLDATI("Villa Soldati"),
    VILLA_URQUIZA("Villa Urquiza");

    private final String displayName;

    Barrio(final String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
