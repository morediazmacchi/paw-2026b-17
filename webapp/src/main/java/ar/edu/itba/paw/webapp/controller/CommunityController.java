package ar.edu.itba.paw.webapp.controller;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

/**
 * Seccion "Comunidad" (feed, descubrir personas, actividad y perfil publico).
 *
 * POR AHORA SOLO DISEÑO: todos los datos son de ejemplo, hardcodeados aca abajo.
 * Cuando se haga la logica, los modelos de ejemplo (Person/Post/...) se reemplazan
 * por los del dominio y los services; las vistas ya reciben todo por el model.
 */
@Controller
public class CommunityController {

    private static final List<Person> PEOPLE = Arrays.asList(
            new Person("lolaplanes", "Lola Ferreyra", "LF", "lola", "Palermo", "2.4k", "Recorriendo Buenos Aires, un plan a la vez. Música en vivo, muestras y lugares nuevos.", false, 2, 214, 1),
            new Person("tomi.rivas", "Tomás Rivas", "TR", "tomas", "Villa Crespo", "1.8k", "Fotógrafo, melómano y productor de encuentros culturales independientes.", true, 2, 301, 0),
            new Person("malecome", "Malena Costa", "MC", "malena", "San Telmo", "5.1k", "Periodista gastronómica. Busco las mejores mesas, ferias y copas de la ciudad.", false, 3, 592, 1),
            new Person("nicopaz", "Nico Paz", "NP", "nico", "Almagro", "980", "Organizo ciclos de stand up y jam sessions en bares de barrio.", false, 4, 120, 0));

    private static final List<EventCard> MALENA_EVENTS = Arrays.asList(
            new EventCard("Hamlet — Versión Contemporánea", "JUE 28 AGO · 20:30HS", "Teatro San Martín — Av. Corrientes 1530", "$2.800", "curtain"),
            new EventCard("Circo Contemporáneo", "SÁB 30 AGO · 18:00HS", "Complejo La Plaza — Av. Corrientes 1660", "$3.000", "curtain"),
            new EventCard("Improvisación Teatral", "DOM 31 AGO · 17:00HS", "Timbre 4 — México 3554", "$1.500", "stage"));

    private static final List<Post> POSTS = Arrays.asList(
            new Post(PEOPLE.get(0), "Hace 24 min",
                    "Anoche fui a la prueba de sonido y les juro que se viene una fecha increíble. ¿Quién más va al Konex este sábado?",
                    new EventCard("Conociendo Rusia en Niceto", "Vie 29 Ago · Palermo", "", "", "concert"), "concert", 148, 23),
            new Post(PEOPLE.get(2), "Hace 1 h",
                    "Guardate este plan: productores de toda la provincia, entrada libre y una selección de quesos que vale el viaje. Nos vemos temprano.",
                    MALENA_EVENTS.get(1), "curtain", 96, 11));

    private static final List<Activity> ACTIVITY = Arrays.asList(
            new Activity("follow", "Hoy", PEOPLE.get(1), "empezó a seguirte.", "Hace 12 minutos", "concert", "following"),
            new Activity("like", "Hoy", PEOPLE.get(2), "y 18 personas indicaron que les gusta tu publicación.", "Hace 34 minutos", "concert", ""),
            new Activity("comment", "Hoy", PEOPLE.get(3), "comentó: “Gran recomendación, nos vemos ahí”.", "Hace 1 hora", "", "reply"),
            new Activity("event", "Esta semana", PEOPLE.get(0), "publicó un nuevo evento: Conociendo Rusia en Niceto.", "Ayer", "concert", ""),
            new Activity("follow", "Esta semana", PEOPLE.get(3), "empezó a seguirte.", "Hace 3 días", "", "follow"));

    @RequestMapping(value = "/comunidad", method = RequestMethod.GET)
    public ModelAndView home() {
        final ModelAndView mav = base("home");
        mav.addObject("posts", POSTS);
        return mav;
    }

    @RequestMapping(value = "/comunidad/descubrir", method = RequestMethod.GET)
    public ModelAndView discover() {
        final ModelAndView mav = base("discover");
        mav.addObject("profiles", PEOPLE);
        return mav;
    }

    @RequestMapping(value = "/comunidad/actividad", method = RequestMethod.GET)
    public ModelAndView activity() {
        final ModelAndView mav = base("activity");
        mav.addObject("items", ACTIVITY);
        return mav;
    }

    @RequestMapping(value = "/comunidad/perfil/{username}", method = RequestMethod.GET)
    public ModelAndView profile(@PathVariable("username") final String username) {
        // Con datos de ejemplo cualquier usuario desconocido cae en el primero.
        Person person = PEOPLE.get(2);
        for (final Person p : PEOPLE) {
            if (p.getUsername().equals(username)) {
                person = p;
            }
        }
        final ModelAndView mav = base("profile");
        mav.addObject("person", person);
        mav.addObject("posts", POSTS.subList(1, 2));
        mav.addObject("organized", MALENA_EVENTS);
        // Estado de ejemplo del boton "Suscribirme a sus eventos".
        mav.addObject("subscribed", false);
        return mav;
    }

    private ModelAndView base(final String active) {
        final ModelAndView mav = new ModelAndView("community/" + active);
        mav.addObject("communityActive", active);
        mav.addObject("suggested", PEOPLE.subList(1, 4));
        mav.addObject("weekPlan", new EventCard("Conociendo Rusia en Niceto", "Vie 29 Ago · Palermo", "", "", "concert"));
        mav.addObject("activityCount", 4);
        return mav;
    }

    // ---- Modelos de ejemplo (se descartan cuando haya logica real) ----

    public static final class Person {
        private final String username, name, initials, tone, neighborhood, followers, bio;
        private final boolean following;
        private final int events, followingCount, postsCount;

        Person(final String username, final String name, final String initials, final String tone,
               final String neighborhood, final String followers, final String bio, final boolean following,
               final int events, final int followingCount, final int postsCount) {
            this.username = username; this.name = name; this.initials = initials; this.tone = tone;
            this.neighborhood = neighborhood; this.followers = followers; this.bio = bio;
            this.following = following; this.events = events; this.followingCount = followingCount;
            this.postsCount = postsCount;
        }

        public String getUsername() { return username; }
        public String getName() { return name; }
        public String getFirstName() { return name.split(" ")[0]; }
        public String getInitials() { return initials; }
        public String getTone() { return tone; }
        public String getNeighborhood() { return neighborhood; }
        public String getFollowers() { return followers; }
        public String getBio() { return bio; }
        public boolean isFollowing() { return following; }
        public int getEvents() { return events; }
        public int getFollowingCount() { return followingCount; }
        public int getPostsCount() { return postsCount; }
    }

    public static final class EventCard {
        private final String title, when, place, price, cover;

        EventCard(final String title, final String when, final String place, final String price, final String cover) {
            this.title = title; this.when = when; this.place = place; this.price = price; this.cover = cover;
        }

        public String getTitle() { return title; }
        public String getWhen() { return when; }
        public String getPlace() { return place; }
        public String getPrice() { return price; }
        public String getCover() { return cover; }
    }

    public static final class Post {
        private final Person author;
        private final String ago, text, image;
        private final EventCard event;
        private final int likes, comments;

        Post(final Person author, final String ago, final String text, final EventCard event,
             final String image, final int likes, final int comments) {
            this.author = author; this.ago = ago; this.text = text; this.event = event;
            this.image = image; this.likes = likes; this.comments = comments;
        }

        public Person getAuthor() { return author; }
        public String getAgo() { return ago; }
        public String getText() { return text; }
        public EventCard getEvent() { return event; }
        public String getImage() { return image; }
        public int getLikes() { return likes; }
        public int getComments() { return comments; }
    }

    public static final class Activity {
        private final String kind, group, text, ago, thumb, action;
        private final Person actor;

        Activity(final String kind, final String group, final Person actor, final String text,
                 final String ago, final String thumb, final String action) {
            this.kind = kind; this.group = group; this.actor = actor; this.text = text;
            this.ago = ago; this.thumb = thumb; this.action = action;
        }

        public String getKind() { return kind; }
        public String getGroup() { return group; }
        public Person getActor() { return actor; }
        public String getText() { return text; }
        public String getAgo() { return ago; }
        public String getThumb() { return thumb; }
        public String getAction() { return action; }
    }
}
