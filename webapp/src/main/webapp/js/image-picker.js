// Selector de fotos de publicar/editar evento (paso 4 del wizard): el <input
// type="file" multiple> nativo reemplaza toda la seleccion cada vez que se
// abre el dialogo, y el recuadro no aceptaba arrastrar y soltar (el navegador
// abria la imagen en la pestana). Este script acumula las fotos en una lista
// propia (click, dialogo o arrastre), las muestra como miniaturas con una X
// para sacar cada una, y despues vuelca la lista al input real con
// DataTransfer: el formulario se envia exactamente igual que antes ("images"
// como lista de archivos), el servidor no se entera de nada.
document.addEventListener("DOMContentLoaded", function () {
    var input = document.getElementById("images");
    var dropzone = document.querySelector(".wizard-dropzone");
    if (!input || !dropzone || typeof DataTransfer === "undefined") {
        return;
    }

    var MAX_IMAGES = 5;
    var selected = [];
    var urls = [];

    var list = document.createElement("div");
    list.className = "image-picker-list";
    var message = document.createElement("p");
    // No usar la clase "error-message": event-wizard.js arranca en el primer
    // paso que la contenga (para mostrar los errores del servidor), y este
    // mensaje, aunque este oculto, hacia que el wizard abriera directo en el
    // paso 4.
    message.className = "image-picker-message";
    message.hidden = true;
    input.parentNode.insertBefore(list, input.nextSibling);
    input.parentNode.insertBefore(message, list);

    function key(file) {
        return file.name + "|" + file.size + "|" + file.lastModified;
    }

    function showMessage(text) {
        message.textContent = text || "";
        message.hidden = !text;
    }

    function add(files) {
        var tooMany = false;
        Array.prototype.forEach.call(files, function (file) {
            if (file.type.indexOf("image/") !== 0) {
                return;
            }
            if (selected.some(function (s) { return key(s) === key(file); })) {
                return;
            }
            if (selected.length >= MAX_IMAGES) {
                tooMany = true;
                return;
            }
            selected.push(file);
        });
        showMessage(tooMany ? (input.getAttribute("data-max-message") || "") : "");
        sync();
    }

    // Vuelca la lista al input real y avisa (evento "change") para que
    // event-wizard.js actualice el resumen del ultimo paso. El flag evita
    // que ese mismo aviso vuelva a entrar en nuestro listener.
    var syncing = false;
    function sync() {
        var transfer = new DataTransfer();
        selected.forEach(function (file) { transfer.items.add(file); });
        syncing = true;
        input.files = transfer.files;
        input.dispatchEvent(new Event("change", {bubbles: true}));
        syncing = false;
        render();
    }

    function render() {
        urls.forEach(function (url) { URL.revokeObjectURL(url); });
        urls = [];
        list.innerHTML = "";
        selected.forEach(function (file, index) {
            var url = URL.createObjectURL(file);
            urls.push(url);

            var item = document.createElement("div");
            item.className = "image-picker-item";

            var img = document.createElement("img");
            img.src = url;
            img.alt = file.name;
            item.appendChild(img);

            if (index === 0) {
                var badge = document.createElement("span");
                badge.className = "image-picker-cover";
                badge.textContent = input.getAttribute("data-cover-label") || "";
                item.appendChild(badge);
            }

            var remove = document.createElement("button");
            remove.type = "button";
            remove.className = "image-picker-remove";
            remove.textContent = "\u00d7";
            remove.setAttribute("aria-label", input.getAttribute("data-remove-label") || "");
            remove.addEventListener("click", function () {
                selected.splice(index, 1);
                showMessage("");
                sync();
            });
            item.appendChild(remove);
            list.appendChild(item);
        });
    }

    input.addEventListener("change", function () {
        if (syncing) {
            return;
        }
        add(input.files);
    });

    ["dragenter", "dragover"].forEach(function (name) {
        dropzone.addEventListener(name, function (event) {
            event.preventDefault();
            dropzone.classList.add("wizard-dropzone-active");
        });
    });
    ["dragleave", "drop"].forEach(function (name) {
        dropzone.addEventListener(name, function (event) {
            event.preventDefault();
            dropzone.classList.remove("wizard-dropzone-active");
        });
    });
    dropzone.addEventListener("drop", function (event) {
        if (event.dataTransfer && event.dataTransfer.files) {
            add(event.dataTransfer.files);
        }
    });
});
