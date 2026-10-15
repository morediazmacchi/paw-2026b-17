// Chat del grupo de un evento (events/chat.jsp). Sin WebSockets: cada POLL_MS pide al
// servidor los mensajes posteriores al ultimo que tiene y los agrega. El servidor
// devuelve el HTML ya escapado, asi que aca nunca se arma HTML con texto del usuario.
(function () {
    'use strict';

    var POLL_MS = 4000;

    var box = document.getElementById('chat-box');
    if (!box) {
        return;
    }
    var list = document.getElementById('chat-messages');
    var empty = document.getElementById('chat-empty');
    var form = document.getElementById('chat-form');
    var input = document.getElementById('chat-input');
    var errorBox = document.getElementById('chat-error');
    var pollUrl = box.getAttribute('data-poll-url');
    var lastId = parseInt(box.getAttribute('data-last-id'), 10) || 0;
    var polling = false;
    var stopped = false;

    function scrollToBottom() {
        box.scrollTop = box.scrollHeight;
    }

    function nearBottom() {
        return box.scrollHeight - box.scrollTop - box.clientHeight < 80;
    }

    function showError(text) {
        if (errorBox) {
            errorBox.textContent = text;
            errorBox.hidden = !text;
        }
    }

    function append(html) {
        var holder = document.createElement('div');
        holder.innerHTML = html;
        var stick = nearBottom();
        var added = false;
        var nodes = holder.querySelectorAll('.chat-msg');
        for (var i = 0; i < nodes.length; i++) {
            var id = parseInt(nodes[i].getAttribute('data-id'), 10);
            if (id > lastId) {
                lastId = id;
                list.appendChild(nodes[i]);
                added = true;
            }
        }
        if (added) {
            if (empty) {
                empty.hidden = true;
            }
            if (stick) {
                scrollToBottom();
            }
        }
    }

    function removedFromGroup() {
        stopped = true;
        showError(box.getAttribute('data-removed-text'));
        if (form) {
            form.hidden = true;
        }
    }

    function poll() {
        if (polling || stopped || document.hidden) {
            return Promise.resolve();
        }
        polling = true;
        return fetch(pollUrl + '?despues=' + lastId, {
            credentials: 'same-origin',
            headers: {'X-Requested-With': 'XMLHttpRequest'}
        }).then(function (response) {
            if (response.status === 403) {
                removedFromGroup();
                return null;
            }
            return response.ok ? response.text() : null;
        }).then(function (html) {
            if (html) {
                append(html);
            }
        }).catch(function () {
            // Sin red por un rato: se reintenta en el proximo ciclo.
        }).then(function () {
            polling = false;
        });
    }

    function send() {
        var text = input.value.trim();
        if (!text) {
            return;
        }
        var button = form.querySelector('button[type=submit]');
        button.disabled = true;
        showError('');
        fetch(form.action, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'X-Requested-With': 'XMLHttpRequest',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
            },
            body: 'text=' + encodeURIComponent(text)
        }).then(function (response) {
            if (response.status === 204) {
                input.value = '';
                // value = '' no dispara "input": se avisa a mano para que el contador
                // (char-counter.js) vuelva a 0 / 1000 y el textarea se achique.
                input.dispatchEvent(new Event('input'));
                return poll().then(scrollToBottom);
            }
            if (response.status === 403 || response.status === 409) {
                // Ya no es miembro o el chat se cerro: la pantalla del servidor lo explica.
                window.location.reload();
                return null;
            }
            showError(box.getAttribute('data-error-text'));
            return null;
        }).catch(function () {
            showError(box.getAttribute('data-error-text'));
        }).then(function () {
            button.disabled = false;
            input.focus();
        });
    }

    function autoGrow() {
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 140) + 'px';
    }

    if (form) {
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            send();
        });
        // Enter envia, Shift+Enter hace un salto de linea (como en WhatsApp Web).
        input.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
                event.preventDefault();
                send();
            }
        });
        input.addEventListener('input', autoGrow);
    }

    scrollToBottom();
    window.setInterval(poll, POLL_MS);
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) {
            poll();
        }
    });
})();
