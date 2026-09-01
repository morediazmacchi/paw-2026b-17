package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Inscripcion;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.InscripcionDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InscripcionServiceImpl implements InscripcionService {

    private final InscripcionDao inscripcionDao;
    private final EventDao eventDao;

    @Autowired
    public InscripcionServiceImpl(final InscripcionDao inscripcionDao, final EventDao eventDao) {
        this.inscripcionDao = inscripcionDao;
        this.eventDao = eventDao;
    }

    @Override
    public String register(final long eventoId, final String email, final String nombre) {
        final Inscripcion existente = inscripcionDao.findByEventoAndEmail(eventoId, email).orElse(null);
        if (existente != null) {
            return existente.getEstado();
        }

        final Event event = eventDao.findById(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("Evento inexistente: " + eventoId));

        String estado = Inscripcion.ESTADO_CONFIRMADA;
        if (event.getCupoMaximo() != null) {
            final long confirmadas = inscripcionDao.countConfirmadas(eventoId);
            if (confirmadas >= event.getCupoMaximo()) {
                estado = Inscripcion.ESTADO_ESPERA;
            }
        }

        final Inscripcion inscripcion = new Inscripcion(eventoId, email, nombre, estado);
        inscripcionDao.create(inscripcion);
        return estado;
    }
}
