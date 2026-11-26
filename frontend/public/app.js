    function icon(name, className = '') {
      return `<svg class="icon ${className}" aria-hidden="true" focusable="false"><use href="#icon-${name}"></use></svg>`;
    }

    function updateThemeIcons() {
      const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
      const name = isDark ? 'sun' : 'moon';
      document.querySelectorAll('#btn-theme-auth, #btn-theme').forEach(button => {
        const use = button.querySelector('use');
        if (use) use.setAttribute('href', `#icon-${name}`);
      });
      document.querySelector('meta[name="theme-color"]').content = isDark ? '#202a27' : '#f4f6f3';
    }

    function toggleUserMenu(forceClose = false) {
      const menu = document.getElementById('user-menu');
      const isOpen = !forceClose && !menu.classList.contains('open');
      menu.classList.toggle('open', isOpen);
      document.getElementById('user-menu-trigger').setAttribute('aria-expanded', isOpen);
    }

    let lastCompactMode = null;
    function syncResponsiveDisclosures() {
      const compactMode = window.matchMedia('(max-width: 640px)').matches;
      if (compactMode === lastCompactMode) return;
      lastCompactMode = compactMode;

      document.querySelectorAll('.task-details').forEach(disclosure => {
        if (!compactMode) {
          disclosure.open = true;
        } else {
          disclosure.open = expandedTaskDetails.has(Number(disclosure.dataset.taskId));
        }
      });
    }

    // ── Configuración de API ──────────────────────────────────────────────────
    const API_BASE = (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')
                     ? 'http://localhost:8080'
                     : (window.location.hostname.endsWith('vercel.app') ? '' : (localStorage.getItem('api_url') || 'https://taskdle.onrender.com'));

    // ── Service Worker & Offline Sync ───────────────────────────
    if ('serviceWorker' in navigator) {
      window.addEventListener('load', () => {
        navigator.serviceWorker.register('./sw.js').catch(err => console.error('SW Error:', err));
      });
    }

    window.addEventListener('online', syncOfflineTasks);

    function saveCache() {
       if (currentUser) localStorage.setItem('cachedTasks_' + currentUser, JSON.stringify(allTasks));
    }

    async function apiFetch(url, options = {}) {
      if (!navigator.onLine) {
        if (options.method && options.method !== 'GET') {
           const id = new Date().getTime();
           let queue = JSON.parse(localStorage.getItem('offlineQueue') || '[]');
           queue.push({ id, url, options });
           localStorage.setItem('offlineQueue', JSON.stringify(queue));
           toast('Registrado sin conexión (se enviará al reconectar)', 'warning');
           return { ok: true, json: async () => ({ ok: true, offline: true, tempId: -id }) };
        }
        return { ok: false, json: async () => ({ ok: false, error: 'Offline' }) };
      }
      try {
        return await fetch(url, options);
      } catch(e) {
        return { ok: false, json: async () => ({ ok: false, error: 'Network' }) };
      }
    }

    async function syncOfflineTasks() {
      let queue = JSON.parse(localStorage.getItem('offlineQueue') || '[]');
      if (queue.length === 0) return;
      
      toast(`Sincronizando ${queue.length} cambios...`, 'info');
      let success = 0;
      for (const item of queue) {
        try {
           const res = await fetch(item.url, item.options);
           if(res.ok) success++;
        } catch(e) {}
      }
      localStorage.removeItem('offlineQueue');
      toast(`Sincronización completada (${success}/${queue.length})`, 'success');
      if (currentUser) loadTareas();
    }

    // ── State ────────────────────────────────────────────────────
    let currentUser = null;
    let allTasks = [];
    let activeFilter = 'ALL';
    let calendarDate;
    let calendarMode = 'month';
    let activeView = 'list';
    let taskMutationVersion = 0;
    let pendingDeadlineWrites = 0;
    let popoverEl = null; // active popover element
    let popoverAnchor = null;
    let popoverDismissTimer = null;
    let selectedTaskIds = new Set();
    let isSelectMode = false;
    let groups = [];
    let taskBeingShared = null;
    let editingTaskId = null;
    let draggedTaskId = null;
    const expandedTaskDetails = new Set();
    const renderedTaskSignatures = new Map();

    // ── Theme ────────────────────────────────────────────────────
    function toggleTheme() {
      document.documentElement.classList.add('theme-switching');
      
      const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
      if (isDark) {
        document.documentElement.removeAttribute('data-theme');
        localStorage.setItem('theme', 'light');
      } else {
        document.documentElement.setAttribute('data-theme', 'dark');
        localStorage.setItem('theme', 'dark');
      }
      updateThemeIcons();

      setTimeout(() => {
        document.documentElement.classList.remove('theme-switching');
      }, 400);
    }

    updateThemeIcons();
    syncResponsiveDisclosures();
    window.addEventListener('resize', syncResponsiveDisclosures);
    document.addEventListener('click', event => {
      if (popoverEl && event.target instanceof Element
        && !popoverEl.contains(event.target)
        && !event.target.closest('.upcoming-more-count, .cal-more-count')) {
        dismissPopover();
      }
      const menu = document.getElementById('user-menu');
      if (menu.classList.contains('open') && !menu.contains(event.target)) toggleUserMenu(true);
      const filters = document.getElementById('filters-disclosure');
      if (filters.open && !filters.contains(event.target)) filters.open = false;
      const viewActions = document.getElementById('view-actions');
      if (viewActions.open && !viewActions.contains(event.target)) viewActions.open = false;
      const headerViewMenu = document.getElementById('header-view-menu');
      if (headerViewMenu.open && !headerViewMenu.contains(event.target)) headerViewMenu.open = false;
    });
    document.addEventListener('toggle', event => {
      const details = event.target;
      if (details.matches('.task-details') && window.matchMedia('(max-width: 640px)').matches) {
        const taskId = Number(details.dataset.taskId);
        if (details.open) expandedTaskDetails.add(taskId);
        else expandedTaskDetails.delete(taskId);
      }
    }, true);

    // ── Auth ─────────────────────────────────────────────────────
    function showTab(tab) {
      document.getElementById('panel-login').style.display = tab === 'login' ? '' : 'none';
      document.getElementById('panel-registro').style.display = tab === 'registro' ? '' : 'none';
      document.getElementById('tab-login').classList.toggle('active', tab === 'login');
      document.getElementById('tab-registro').classList.toggle('active', tab === 'registro');
      document.getElementById('err-login').classList.remove('visible');
      document.getElementById('err-registro').classList.remove('visible');
    }

    async function doLogin() {
      const user = document.getElementById('l-user').value.trim();
      const pass = document.getElementById('l-pass').value;
      if (!user || !pass) { showErr('err-login', 'Completa todos los campos'); return; }

      setBtnLoading('btn-login', 'login-label', true);
      try {
        const res = await apiFetch(API_BASE + '/api/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ usuario: user, password: pass })
        });
        const data = await res.json();
        if (data.ok) {
          currentUser = data.usuario;
          enterDashboard();
        } else {
          if (data.error === 'Network' || !res.ok) {
            showErr('err-login', 'No se pudo conectar con el servidor (' + API_BASE + ')');
          } else {
            showErr('err-login', data.mensaje || 'Error al iniciar sesión');
          }
        }
      } catch (e) {
        showErr('err-login', 'Error de conexión con el servidor');
      }
      setBtnLoading('btn-login', 'login-label', false, 'Iniciar sesión');
    }

    async function doRegistro() {
      const user = document.getElementById('r-user').value.trim();
      const pass = document.getElementById('r-pass').value;
      const pass2 = document.getElementById('r-pass2').value;
      if (!user || !pass || !pass2) { showErr('err-registro', 'Completa todos los campos'); return; }
      if (pass !== pass2) { showErr('err-registro', 'Las contraseñas no coinciden'); return; }

      setBtnLoading('btn-registro', 'registro-label', true);
      try {
        const res = await apiFetch(API_BASE + '/api/registro', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ usuario: user, password: pass })
        });
        const data = await res.json();
        if (data.ok) {
          currentUser = data.usuario;
          enterDashboard();
          toast('¡Cuenta creada! Bienvenido', 'success');
        } else {
          if (data.error === 'Network' || !res.ok) {
            showErr('err-registro', 'No se pudo conectar con el servidor (' + API_BASE + ')');
          } else {
            showErr('err-registro', data.mensaje || 'Error al registrar');
          }
        }
      } catch (e) {
        showErr('err-registro', 'Error de conexión con el servidor');
      }
      setBtnLoading('btn-registro', 'registro-label', false, 'Crear cuenta');
    }

    function doLogout() {
      toggleUserMenu(true);
      stopAutoSync();
      localStorage.removeItem('taskdle_user');
      currentUser = null;
      allTasks = [];
      exitSelectMode();
      document.getElementById('auth-page').style.display = 'flex';
      document.getElementById('dashboard-page').style.display = 'none';
      document.getElementById('dashboard-page').classList.remove('active');
      document.getElementById('l-user').value = '';
      document.getElementById('l-pass').value = '';
      showTab('login');
    }

    // ── Dashboard ─────────────────────────────────────────────────
    function enterDashboard() {
      if (currentUser) localStorage.setItem('taskdle_user', currentUser);
      document.getElementById('auth-page').style.display = 'none';
      document.getElementById('dashboard-page').style.display = 'flex';
      setTimeout(() => document.getElementById('dashboard-page').classList.add('active'), 10);
      document.getElementById('user-name-label').textContent = currentUser;
      document.getElementById('user-menu-heading').textContent = currentUser;
      document.getElementById('user-avatar').textContent = currentUser[0].toUpperCase();
      const now = new Date();
      calendarDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      loadTareas();
      loadGroups();
      startAutoSync();
    }

    function renderTaskSnapshot() {
      const validIds = new Set(allTasks.map(t => t.id));
      for (const id of selectedTaskIds) {
        if (!validIds.has(id)) selectedTaskIds.delete(id);
      }

      updateBulkBar();
      renderUpcomingDays();
      renderTareas();
      if (activeView === 'calendar') renderCalendar();
    }

    async function loadTareas() {
      if (pendingDeadlineWrites > 0) return;
      const mutationVersion = taskMutationVersion;
      const cacheKey = 'cachedTasks_' + currentUser;
      if (!navigator.onLine) {
        allTasks = JSON.parse(localStorage.getItem(cacheKey) || '[]');
        renderTaskSnapshot();
        return;
      }

      let renderedCachedTasks = false;
      try {
        const cachedTasks = localStorage.getItem(cacheKey);
        if (cachedTasks !== null) {
          const parsedTasks = JSON.parse(cachedTasks);
          if (Array.isArray(parsedTasks)) {
            const cachedTasksChanged = JSON.stringify(allTasks) !== JSON.stringify(parsedTasks);
            const gridHasContent = document.getElementById('task-grid').childElementCount > 0;
            allTasks = parsedTasks;
            if (cachedTasksChanged || !gridHasContent) renderTaskSnapshot();
            renderedCachedTasks = true;
          } else {
            console.error('La caché de tareas no tiene un formato válido');
          }
        }
      } catch (e) {
        console.error('No se pudo recuperar la caché de tareas', e);
      }

      try {
        const res = await apiFetch(API_BASE + `/api/tareas?usuario=${encodeURIComponent(currentUser)}`);
        if (!res.ok) throw new Error('La solicitud de tareas no fue exitosa');
        const tasks = await res.json();
        if (!Array.isArray(tasks)) throw new Error('La respuesta de tareas no tiene un formato válido');
        if (mutationVersion !== taskMutationVersion || pendingDeadlineWrites > 0) return;
        const tasksChanged = JSON.stringify(allTasks) !== JSON.stringify(tasks);
        allTasks = tasks;
        if (!renderedCachedTasks || tasksChanged) {
          saveCache();
          renderTaskSnapshot();
        }
      } catch (e) {
        console.error('Error al cargar las tareas', e);
        toast(allTasks.length ? 'Sin conexión con el servidor; se muestran las tareas guardadas' : 'Error al cargar las tareas', 'error');
      }
    }

    // ── Auto-Sincronización en Tiempo Real (Tareas Compartidas) ──
    let autoSyncTimer = null;
    let isSyncing = false;

    function startAutoSync() {
      stopAutoSync();
      autoSyncTimer = setInterval(syncBackground, 3500);
    }

    function stopAutoSync() {
      if (autoSyncTimer) {
        clearInterval(autoSyncTimer);
        autoSyncTimer = null;
      }
    }

    async function syncBackground() {
      if (!currentUser || isSyncing || pendingDeadlineWrites > 0 || !navigator.onLine) return;
      if (document.hidden) return; // no gastar peticiones si la pestaña está oculta

      isSyncing = true;
      const mutationVersion = taskMutationVersion;
      try {
        const res = await apiFetch(API_BASE + `/api/tareas?usuario=${encodeURIComponent(currentUser)}`);
        if (!res.ok) { isSyncing = false; return; }
        const newTasks = await res.json();
        if (mutationVersion !== taskMutationVersion || pendingDeadlineWrites > 0) return;

        // Comparar con las tareas actuales
        const oldJson = JSON.stringify(allTasks);
        const newJson = JSON.stringify(newTasks);

        if (oldJson !== newJson) {
          // Detectar si han entrado tareas nuevas (por ejemplo compartidas por otro usuario)
          const oldIds = new Set(allTasks.map(t => t.id));
          const recienLlegadas = newTasks.filter(t => !oldIds.has(t.id));

          if (recienLlegadas.length > 0 && allTasks.length > 0) {
            const t = recienLlegadas[0];
            toast(`Nueva tarea recibida: "${t.titulo}"`, 'info');
          }

          allTasks = newTasks;
          saveCache();

          const validIds = new Set(allTasks.map(t => t.id));
          for (const id of selectedTaskIds) {
             if (!validIds.has(id)) selectedTaskIds.delete(id);
          }
          updateBulkBar();
          renderUpcomingDays();
          renderTareas();
          if (activeView === 'calendar') renderCalendar();
        }
      } catch (ignored) {
      } finally {
        isSyncing = false;
      }
    }

    // Al volver a la pestaña o darle foco, sincronizar al instante
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && currentUser) syncBackground();
    });
    window.addEventListener('focus', () => {
      if (currentUser) syncBackground();
    });

    // ── View Toggle ───────────────────────────────────────────────
    function showView(v) {
      activeView = v;
      const dashboard = document.getElementById('dashboard-page');
      dashboard.dataset.view = v;
      const listView = document.getElementById('list-view');
      const calView = document.getElementById('calendar-view');
      const rulView = document.getElementById('ruleta-view');
      const filters = document.getElementById('filters-disclosure');
      const viewLabels = { list: 'Lista', calendar: 'Calendario', ruleta: 'Ruleta' };
      document.getElementById('current-view-label').textContent = viewLabels[v];
      document.querySelectorAll('.header-view-option').forEach(option => {
        option.hidden = option.dataset.view === v;
      });
      document.getElementById('header-view-menu').open = false;
      const viewActions = document.getElementById('view-actions');
      viewActions.hidden = v !== 'list';
      if (viewActions.hidden) viewActions.open = false;
      filters.hidden = v !== 'list';
      if (filters.hidden) filters.open = false;
      document.getElementById('upcoming-section').hidden = v === 'calendar';
      if (v === 'list') {
        listView.style.display = '';
        calView.classList.remove('visible');
        rulView.classList.remove('visible');
      } else if (v === 'calendar') {
        listView.style.display = 'none';
        calView.classList.add('visible');
        rulView.classList.remove('visible');
        renderCalendar();
      } else {
        listView.style.display = 'none';
        calView.classList.remove('visible');
        rulView.classList.add('visible');
        document.getElementById('ruleta-result').innerHTML = `<div class="empty-state" style="padding:2rem 0">${icon('shuffle', 'empty-icon')}<p>Gira la ruleta para obtener tu próxima tarea.</p></div>`;
      }
      closeViewActions();
      dismissPopover();
    }

    function closeViewActions() {
      document.getElementById('view-actions').open = false;
    }

    function setFilter(btn) {
      document.querySelectorAll('.filter-btn[data-filter]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      activeFilter = btn.dataset.filter;
      document.getElementById('filters-disclosure').open = false;
      renderTareas();
    }

    function renderUpcomingDays() {
      const container = document.getElementById('upcoming-days');
      const today = new Date();
      const days = Array.from({ length: 7 }, (_, offset) =>
        new Date(today.getFullYear(), today.getMonth(), today.getDate() + offset)
      );
      const tasksByDay = new Map();

      allTasks.forEach(task => {
        if (!task.fechaLimite) return;
        const key = deadlineDateKey(task.fechaLimite);
        if (!key) return;
        if (!tasksByDay.has(key)) tasksByDay.set(key, []);
        tasksByDay.get(key).push(task);
      });

      const stateLabels = {
        PENDIENTE: 'Pendiente',
        EN_PROGRESO: 'En progreso',
        COMPLETADA: 'Completada'
      };

      container.innerHTML = days.map((date, offset) => {
        const key = isoDate(date.getFullYear(), date.getMonth(), date.getDate());
        const tasks = (tasksByDay.get(key) || [])
          .slice()
          .sort((a, b) => parseDeadline(a.fechaLimite) - parseDeadline(b.fechaLimite));
        tasksByDay.set(key, tasks);
        const weekday = offset === 0
          ? 'Hoy'
          : date.toLocaleDateString('es-ES', { weekday: 'long' });
        const dateLabel = date.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });
        const firstTask = tasks[0];
        const taskList = firstTask
          ? `<ul class="upcoming-task-list"><li class="upcoming-task ${firstTask.prioridad.toLowerCase()}${firstTask.estado === 'COMPLETADA' ? ' completed' : ''}" data-task-id="${escHtml(String(firstTask.id))}">
                <span class="upcoming-task-title">${escHtml(firstTask.titulo)}</span>
                <span class="upcoming-task-meta">
                  <time class="upcoming-task-time">${escHtml(formatDeadlineTime(firstTask.fechaLimite))}</time>
                  <span class="upcoming-task-state">${escHtml(stateLabels[firstTask.estado] || firstTask.estado)}</span>
                </span>
              </li></ul>
              ${tasks.length > 1 ? `<button class="upcoming-more-count" type="button" title="Mostrar ${tasks.length - 1} tareas más" aria-label="Mostrar ${tasks.length - 1} tareas más" aria-expanded="false">+${tasks.length - 1}</button>` : ''}`
          : '<p class="upcoming-empty">Sin tareas</p>';

        return `
          <article class="upcoming-day${offset === 0 ? ' today' : ''}" data-date="${key}" role="button" tabindex="0" aria-label="Crear tarea para el ${date.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })}">
            <div class="upcoming-day-header">
              <div>
                <h4>${weekday}</h4>
                <time datetime="${key}">${dateLabel}</time>
              </div>
              <span class="upcoming-day-count" aria-label="${tasks.length} tareas">${tasks.length}</span>
            </div>
            ${taskList}
          </article>
        `;
      }).join('');

      const tasksById = new Map(allTasks.map(task => [String(task.id), task]));
      container.querySelectorAll('.upcoming-day').forEach(day => {
        const createTaskForDay = () => openModalForDate(day.dataset.date);
        day.addEventListener('click', createTaskForDay);
        day.addEventListener('keydown', event => {
          if (event.target !== day || (event.key !== 'Enter' && event.key !== ' ')) return;
          event.preventDefault();
          createTaskForDay();
        });
        enableCalendarDrop(day);
        day.querySelectorAll('.upcoming-task').forEach(element => {
          const task = tasksById.get(element.dataset.taskId);
          if (task) enableCalendarTaskInteractions(element, task);
        });
        const moreButton = day.querySelector('.upcoming-more-count');
        if (moreButton) enableMoreTasksPopover(moreButton, tasksByDay.get(day.dataset.date).slice(1));
      });
    }

    function renderTareas() {
      const grid = document.getElementById('task-grid');
      let tasks = allTasks;
      if (activeFilter === 'VENCIDA') tasks = tasks.filter(t => t.vencida);
      else if (activeFilter !== 'ALL') tasks = tasks.filter(t => t.estado === activeFilter);

      if (tasks.length === 0) {
        const msg = activeFilter === 'ALL'
          ? 'Aún no tienes tareas. ¡Crea una con el botón ＋!'
          : 'No hay tareas para este filtro.';
        renderedTaskSignatures.clear();
        grid.innerHTML = `<div class="empty-state">${icon('clipboard', 'empty-icon')}<p>${msg}</p></div>`;
        return;
      }

      const emptyState = grid.querySelector('.empty-state');
      if (emptyState) emptyState.remove();
      const currentCards = new Map(
        Array.from(grid.querySelectorAll('.task-card'), card => [card.id, card])
      );

      tasks.forEach((task, index) => {
        const cardId = `card-${task.id}`;
        let card = currentCards.get(cardId);
        const signature = JSON.stringify(task);

        if (!card || renderedTaskSignatures.get(cardId) !== signature) {
          const template = document.createElement('template');
          template.innerHTML = taskCardHTML(task).trim();
          const updatedCard = template.content.firstElementChild;
          if (!updatedCard) throw new Error('No se pudo crear la tarjeta de tarea');
          if (card) card.replaceWith(updatedCard);
          card = updatedCard;
          renderedTaskSignatures.set(cardId, signature);
        }

        currentCards.delete(cardId);
        if (grid.children[index] !== card) {
          grid.insertBefore(card, grid.children[index] || null);
        }
      });

      currentCards.forEach((card, cardId) => {
        card.remove();
        renderedTaskSignatures.delete(cardId);
      });
    }

    function taskCardHTML(t) {
      const prioClass = t.prioridad.toLowerCase();
      const prioBadge = { ALTA: 'badge-alta', MEDIA: 'badge-media', BAJA: 'badge-baja' }[t.prioridad] || 'badge-media';
      const titleClass = t.estado === 'COMPLETADA' ? 'completada-title' : '';
      const cardClass = t.vencida ? 'vencida-card' : '';

      let metaInfo = `<span>${icon('calendar-date')} Creada: ${t.fechaCreacion}</span>`;
      const frecuenciaLabel = { DIARIA: 'cada día', SEMANAL: 'cada semana', MENSUAL: 'cada mes' };
      if (t.recurrenciaFrecuencia && t.recurrenciaActiva !== false) {
        metaInfo += `<span>${icon('repeat')} Se repite ${frecuenciaLabel[t.recurrenciaFrecuencia] || 'periódicamente'}</span>`;
      }
      if (t.fechaLimite) {
        if (t.vencida) {
          metaInfo += `<span class="vencida-tag">${icon('alert')} Vencida · ${formatDeadline(t.fechaLimite)}</span>`;
        } else if (t.estado !== 'COMPLETADA') {
          const dias = getDaysUntilDeadline(t.fechaLimite);
          const tag = dias === 0 ? '¡Vence hoy!'
            : dias === 1 ? 'Vence mañana'
              : `${dias} días restantes`;
          metaInfo += `<span>${icon('calendar-date')} Límite: ${formatDeadline(t.fechaLimite)} · ${tag}</span>`;
        } else {
          metaInfo += `<span class="ok-tag">${icon('check')} Completada · Límite: ${formatDeadline(t.fechaLimite)}</span>`;
        }
      }

      const stateLabel = { PENDIENTE: 'Pendiente', EN_PROGRESO: 'En progreso', COMPLETADA: 'Completada' };

      // Subtareas HTML
      let subtareasHtml = '';
      let subtareasResumen = '';
      if (t.subtareas && t.subtareas.length > 0) {
        const total = t.subtareas.length;
        const hechas = t.subtareas.filter(s => s.completada).length;
        const pct = Math.round((hechas / total) * 100);
        subtareasResumen = `${hechas}/${total} pasos`;
        
        let sList = t.subtareas.map((st, i) => `
          <label style="display:flex; align-items:center; gap:.5rem; font-size:.82rem; cursor:pointer;" onclick="event.stopPropagation()">
            <input type="checkbox" ${st.completada ? 'checked' : ''} onchange="toggleSubtarea(${t.id}, ${st.id}, this.checked)" style="accent-color:var(--primary); width:14px; height:14px;">
            <span style="${st.completada ? 'text-decoration:line-through;color:var(--muted);' : ''} flex:1; min-width:0; line-height:1.3; overflow-wrap:anywhere;">${escHtml(st.titulo)}</span>
          </label>
        `).join('');

        subtareasHtml = `
          <div style="margin-top:.6rem; padding-top:.6rem; border-top:1px dashed var(--border);">
            <div style="font-size:.72rem; color:var(--muted); margin-bottom:.4rem; display:flex; justify-content:space-between; font-weight:600;">
              <span>Sub-tareas</span>
              <span style="${hechas === total ? 'color:var(--success)' : ''}">${hechas}/${total} (${pct}%)</span>
            </div>
            <div style="width:100%; height:4px; background:var(--surface2); border-radius:99px; overflow:hidden; margin-bottom:.8rem;">
              <div style="height:100%; width:${pct}%; background:${hechas===total ? 'var(--success)' : 'var(--primary)'}; transition:width .4s ease, background-color .4s;"></div>
            </div>
            <div style="display:flex; flex-direction:column; gap:.4rem;">
              ${sList}
            </div>
          </div>
        `;
      }

      const detallesContenido = [
        t.descripcion ? `<div class="task-desc">${escHtml(t.descripcion)}</div>` : '',
        subtareasHtml
      ].filter(Boolean).join('');
      const detallesTitulo = subtareasHtml ? 'Detalles' : 'Descripción';
      const detallesHtml = detallesContenido ? `
        <details class="task-details" data-task-id="${t.id}" ${window.matchMedia('(max-width: 640px)').matches ? (expandedTaskDetails.has(Number(t.id)) ? 'open' : '') : 'open'}>
          <summary class="task-details-summary" onclick="this.parentElement.dataset.userToggled='true'">
            <span>${detallesTitulo}</span>
            ${subtareasResumen ? `<span class="task-details-count">${subtareasResumen}</span>` : ''}
            ${icon('chevron-down', 'task-details-chevron')}
          </summary>
          <div class="task-details-content">${detallesContenido}</div>
        </details>
      ` : '';
      const recurrenceAction = t.recurrenciaActiva && t.recurrenciaPuedeDetener
        ? `<button class="btn-delete" onclick="detenerRepeticion(${t.id})" title="Detener repetición" aria-label="Detener repetición">${icon('repeat')}</button>`
        : '';
      const editAction = t.editable === false ? ''
        : `<button class="btn-delete btn-edit" onclick="editarTarea(${t.id})" title="Editar tarea" aria-label="Editar tarea">${icon('edit')}</button>`;

      return `
  <div class="task-card ${prioClass} ${cardClass}" id="card-${t.id}">
    <div class="task-header">
      <input type="checkbox" class="task-checkbox" onchange="toggleTaskSelection(${t.id}, this)" ${selectedTaskIds.has(t.id) ? 'checked' : ''}>
      <div class="task-title ${titleClass}">${escHtml(t.titulo)}</div>
      <span class="badge ${prioBadge}">${t.prioridad}</span>
    </div>
    ${detallesHtml}
    <div class="task-meta">${metaInfo}</div>
    <div class="task-footer">
      <select class="state-select ${t.estado}" id="sel-${t.id}"
        onchange="cambiarEstado(${t.id}, this.value, this)">
        <option value="PENDIENTE"   ${t.estado === 'PENDIENTE' ? 'selected' : ''}>${stateLabel.PENDIENTE}</option>
        <option value="EN_PROGRESO" ${t.estado === 'EN_PROGRESO' ? 'selected' : ''}>${stateLabel.EN_PROGRESO}</option>
        <option value="COMPLETADA"  ${t.estado === 'COMPLETADA' ? 'selected' : ''}>${stateLabel.COMPLETADA}</option>
      </select>
      ${editAction}
      <button class="btn-delete" onclick="compartirTarea(${t.id})" title="Compartir tarea" aria-label="Compartir tarea" style="border-color: var(--primary); color: var(--primary);">${icon('share')}</button>
      ${recurrenceAction}
      <button class="btn-delete" onclick="eliminarTarea(${t.id})" title="Eliminar tarea" aria-label="Eliminar tarea">${icon('trash')}</button>
    </div>
  </div>`;
    }

    // ── CRUD Actions ──────────────────────────────────────────────
    async function cambiarEstado(id, estado, sel) {
      sel.className = `state-select ${estado}`;
      try {
        const res = await apiFetch(API_BASE + `/api/tareas/${id}/estado`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ usuario: currentUser, estado })
        });
        const data = await res.json();
        if (data.ok) {
          const t = allTasks.find(x => x.id === id);
          if (t) t.estado = estado;
          saveCache();
          renderUpcomingDays();
          renderTareas();
          if (data.ocurrenciasCreadas === -1) {
             toast('Tarea completada, pero no se pudo programar la siguiente repetición', 'error');
          } else {
             toast('Estado actualizado ✓', 'success');
          }
          if (data.ocurrenciasCreadas > 0) await loadTareas();
        }
      } catch (e) { toast('Error al actualizar', 'error'); }
    }

    async function eliminarTarea(id) {
      if (!confirm('¿Eliminar esta tarea? Esta acción no se puede deshacer.')) return;
      try {
        const res = await apiFetch(API_BASE + `/api/tareas/${id}?usuario=${encodeURIComponent(currentUser)}`, {
          method: 'DELETE'
        });
        const data = await res.json();
        if (data.ok) {
          allTasks = allTasks.filter(t => t.id !== id);
          saveCache();
          renderUpcomingDays();
          renderTareas();
          toast('Tarea eliminada', 'success');
        }
      } catch (e) { toast('Error al eliminar', 'error'); }
    }

    async function detenerRepeticion(id) {
      if (!confirm('¿Detener esta repetición y quitar las próximas tareas programadas?')) return;
      const task = allTasks.find(item => item.id === id);
      if (!task || !task.recurrenciaFrecuencia) return;

      try {
        if (id < 0) {
          const queue = JSON.parse(localStorage.getItem('offlineQueue') || '[]');
          const queuedCreate = queue.find(item => item.id === -id && item.url === API_BASE + '/api/tareas');
          if (!queuedCreate) throw new Error('No se encontró la tarea pendiente de sincronización');
          const queuedBody = JSON.parse(queuedCreate.options.body);
          delete queuedBody.frecuenciaRepeticion;
          delete queuedBody.zonaHoraria;
          queuedCreate.options.body = JSON.stringify(queuedBody);
          localStorage.setItem('offlineQueue', JSON.stringify(queue));
          task.recurrenciaFrecuencia = null;
          task.recurrenciaActiva = false;
          saveCache();
          renderTareas();
          toast('Repetición cancelada antes de sincronizar', 'success');
          return;
        }

        const res = await apiFetch(API_BASE + `/api/tareas/${id}/repeticion/detener`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ usuario: currentUser })
        });
        const data = await res.json();
        if (!res.ok || !data.ok) throw new Error(data.mensaje || 'No se pudo detener la repetición');

        if (data.offline) {
          const serieId = task.recurrenciaSerieId;
          const indice = task.recurrenciaIndice;
          allTasks = allTasks.filter(item => !(
            item.recurrenciaSerieId === serieId
            && item.recurrenciaIndice > indice
            && item.estado !== 'COMPLETADA'
          ));
          allTasks.filter(item => item.recurrenciaSerieId === serieId).forEach(item => {
            item.recurrenciaFrecuencia = null;
            item.recurrenciaSerieId = null;
            item.recurrenciaIndice = null;
            item.recurrenciaActiva = false;
          });
          saveCache();
          renderTareas();
          toast('Se detendrá al reconectar', 'success');
        } else {
          await loadTareas();
          toast('Repetición detenida', 'success');
        }
      } catch (e) {
        toast(e.message || 'No se pudo detener la repetición', 'error');
      }
    }

    async function compartirTarea(id) {
      taskBeingShared = id;
      document.getElementById('share-user').value = '';
      document.getElementById('share-modal-overlay').classList.add('open');
      await loadGroups();
      document.getElementById('share-modal-overlay').querySelector('input').focus();
    }

    function closeShareModal() {
      document.getElementById('share-modal-overlay').classList.remove('open');
      taskBeingShared = null;
    }

    async function compartirTareaUsuario() {
      const user = document.getElementById('share-user').value.trim();
      if (!taskBeingShared || !user) return;
      try {
        const res = await apiFetch(API_BASE + `/api/tareas/${taskBeingShared}/compartir`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ usuario: currentUser, compartirCon: user.trim() })
        });
        const data = await res.json();
        if (data.ok) {
          toast(`Tarea compartida con ${user}`, 'success');
          closeShareModal();
        } else {
          toast(data.mensaje || 'Error al compartir. Verifica el usuario.', 'error');
        }
      } catch (e) {
        toast('Error de conexión', 'error');
      }
    }

    async function compartirTareaGrupo() {
      if (!taskBeingShared) return;
      const select = document.getElementById('share-group');
      const groupId = Number(select.value);
      if (!groupId) {
        toast('Selecciona un grupo', 'error');
        return;
      }
      try {
        const res = await apiFetch(API_BASE + `/api/grupos/${groupId}/tareas/${taskBeingShared}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ usuario: currentUser })
        });
        const data = await res.json();
        if (data.ok) {
          toast('Tarea compartida con el grupo', 'success');
          closeShareModal();
        } else {
          toast(data.mensaje || 'No se pudo compartir con el grupo', 'error');
        }
      } catch (e) {
        toast('Error de conexión', 'error');
      }
    }

    function openGroupsModal() {
      document.getElementById('groups-modal-overlay').classList.add('open');
      document.getElementById('group-name').value = '';
      loadGroups();
      document.getElementById('group-name').focus();
    }

    function closeGroupsModal() {
      document.getElementById('groups-modal-overlay').classList.remove('open');
    }

    async function loadGroups() {
      if (!currentUser) return;
      try {
        const res = await apiFetch(API_BASE + `/api/grupos?usuario=${encodeURIComponent(currentUser)}`);
        const data = await res.json();
        if (!Array.isArray(data)) return;
        groups = data;
        renderGroups();
        const select = document.getElementById('share-group');
        const ownedGroups = groups.filter(group => group.propietario === currentUser);
        select.innerHTML = ownedGroups.length
          ? ownedGroups.map(group => `<option value="${group.id}">${escHtml(group.nombre)} (${group.miembros.length})</option>`).join('')
          : '<option value="">No tienes grupos propios</option>';
        document.getElementById('share-group-btn').disabled = ownedGroups.length === 0;
      } catch (e) {
        groups = [];
        renderGroups();
      }
    }

    function renderGroups() {
      const container = document.getElementById('groups-list');
      if (!groups.length) {
        container.innerHTML = '<div class="empty-state" style="padding:1.2rem"><p>Aún no tienes grupos.</p></div>';
        return;
      }
      container.innerHTML = groups.map(group => {
        const owner = group.propietario === currentUser;
        const memberRows = group.miembros.map(miembro => `
          <div style="display:flex;align-items:center;justify-content:space-between;gap:.5rem;padding:.35rem 0">
            <span>${escHtml(miembro)}</span>
            ${owner ? `<button class="btn-close" type="button" onclick="quitarMiembroGrupo(${group.id}, '${escHtml(miembro).replace(/'/g, '&#39;')}')" aria-label="Quitar a ${escHtml(miembro)}">${icon('close')}</button>` : ''}
          </div>`).join('');
        return `
          <section style="border:1px solid var(--border);border-radius:12px;padding:.8rem;background:var(--surface2)">
            <div style="display:flex;align-items:center;justify-content:space-between;gap:.5rem">
              <div>
                <strong style="display:block">${escHtml(group.nombre)}</strong>
                <span style="color:var(--muted);font-size:.75rem">${owner ? 'Propietario' : `Compartido por ${escHtml(group.propietario)}`}</span>
              </div>
              ${owner ? `<button class="btn-close" type="button" onclick="eliminarGrupo(${group.id})" aria-label="Eliminar grupo">${icon('trash')}</button>` : ''}
            </div>
            ${owner ? `
              <div class="form-row" style="grid-template-columns:minmax(0,1fr) auto;margin-top:.7rem">
                <input type="text" id="member-${group.id}" placeholder="Usuario amigo" style="width:100%">
                <button class="btn-primary-modal" type="button" onclick="agregarMiembroGrupo(${group.id})" style="padding:.72rem">Añadir</button>
              </div>` : ''}
            <div style="margin-top:.55rem;border-top:1px solid var(--border);font-size:.82rem">${memberRows || '<p style="color:var(--muted);padding:.45rem 0">Sin miembros</p>'}</div>
          </section>`;
      }).join('');
    }

    async function crearGrupo() {
      const nombre = document.getElementById('group-name').value.trim();
      if (!nombre) {
        toast('Escribe el nombre del grupo', 'error');
        return;
      }
      const res = await apiFetch(API_BASE + '/api/grupos', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usuario: currentUser, nombre })
      });
      const data = await res.json();
      if (data.ok) {
        document.getElementById('group-name').value = '';
        await loadGroups();
        toast('Grupo creado', 'success');
      } else {
        toast(data.mensaje || 'No se pudo crear el grupo', 'error');
      }
    }

    async function agregarMiembroGrupo(grupoId) {
      const input = document.getElementById(`member-${grupoId}`);
      const miembro = input.value.trim();
      if (!miembro) return;
      const res = await apiFetch(API_BASE + `/api/grupos/${grupoId}/miembros`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usuario: currentUser, miembro })
      });
      const data = await res.json();
      if (data.ok) {
        await loadGroups();
        toast('Amigo añadido al grupo', 'success');
      } else {
        toast(data.mensaje || 'No se pudo añadir al amigo', 'error');
      }
    }

    async function quitarMiembroGrupo(grupoId, miembro) {
      const res = await apiFetch(API_BASE + `/api/grupos/${grupoId}/miembros`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usuario: currentUser, miembro })
      });
      const data = await res.json();
      if (data.ok) {
        await loadGroups();
        toast('Miembro retirado del grupo', 'success');
      } else {
        toast(data.mensaje || 'No se pudo retirar al miembro', 'error');
      }
    }

    async function eliminarGrupo(grupoId) {
      if (!confirm('¿Eliminar este grupo? Las tareas compartidas no se eliminarán.')) return;
      const res = await apiFetch(API_BASE + `/api/grupos/${grupoId}?usuario=${encodeURIComponent(currentUser)}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (data.ok) {
        await loadGroups();
        toast('Grupo eliminado', 'success');
      } else {
        toast('No se pudo eliminar el grupo', 'error');
      }
    }

    function toggleTaskSelection(id, checkbox) {
       if (checkbox.checked) selectedTaskIds.add(id);
       else selectedTaskIds.delete(id);
       updateBulkBar();
    }

    function addSubtareaInput(value = '') {
      const list = document.getElementById('subtareas-list');
      const div = document.createElement('div');
      div.className = 'subtask-row';
      div.innerHTML = `
        <input type="text" class="subtarea-input" placeholder="Ej: Comprar harina">
        <button type="button" class="btn-delete" onclick="this.parentElement.remove()" aria-label="Eliminar paso">${icon('close')}</button>
      `;
      div.querySelector('input').value = value;
      list.appendChild(div);
      div.querySelector('input').focus();
    }

    async function toggleSubtarea(id, subtareaId, completada) {
      try {
        const res = await apiFetch(API_BASE + `/api/tareas/${id}/subtarea`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ usuario: currentUser, subtareaId, completada })
        });
        const data = await res.json();
        if (data.ok) {
           if (data.ocurrenciasCreadas === -1) {
               toast('Paso completado, pero no se pudo programar la siguiente repetición', 'error');
           } else if (data.nuevoEstado === 'COMPLETADA') {
               toast('Tarea auto-completada', 'success');
           }
           loadTareas();
        } else {
           toast('Error al actualizar paso', 'error');
        }
      } catch (e) { toast('Error de conexión', 'error'); }
    }

    function updateBulkBar() {
       const bar = document.getElementById('bulk-action-bar');
       const count = document.getElementById('bulk-count');
       if (selectedTaskIds.size > 0) {
           bar.style.display = 'flex';
           count.textContent = selectedTaskIds.size;
       } else {
           bar.style.display = 'none';
       }
    }

    async function borrarSeleccionadas() {
       if (!confirm(`¿Estás seguro de que deseas borrar ${selectedTaskIds.size} tareas?`)) return;
       
       const btn = document.querySelector('.btn-bulk-delete');
       btn.disabled = true;
       btn.textContent = 'Borrando...';
       
       try {
           const ids = Array.from(selectedTaskIds);
           for (const id of ids) {
               await apiFetch(API_BASE + `/api/tareas/${id}?usuario=${encodeURIComponent(currentUser)}`, { method: 'DELETE' });
               allTasks = allTasks.filter(t => t.id !== id);
           }
           saveCache();
           exitSelectMode();
           renderUpcomingDays();
           renderTareas();
           toast('Tareas eliminadas', 'success');
       } catch(e) {
           toast('Error al borrar. Algunas tareas podrían no haberse eliminado.', 'error');
           loadTareas();
       }
       btn.disabled = false;
       btn.innerHTML = `${icon('trash')} Borrar (<span id="bulk-count">${selectedTaskIds.size}</span>)`;
    }

    function toggleSelectMode() {
      isSelectMode = !isSelectMode;
      const btn = document.getElementById('btn-toggle-select');
      const btnAll = document.getElementById('btn-select-all');
      const grid = document.getElementById('task-grid');
      
      if (isSelectMode) {
          btn.classList.add('active');
          btn.textContent = 'Cancelar';
          btnAll.style.display = 'inline-block';
          grid.classList.add('select-mode');
      } else {
          exitSelectMode();
      }
    }

    function exitSelectMode() {
        isSelectMode = false;
        const btn = document.getElementById('btn-toggle-select');
        const btnAll = document.getElementById('btn-select-all');
        const grid = document.getElementById('task-grid');
        if(btn) {
           btn.classList.remove('active');
           btn.innerHTML = `${icon('select')} Seleccionar`;
        }
        if(btnAll) btnAll.style.display = 'none';
        if(grid) grid.classList.remove('select-mode');
        selectedTaskIds.clear();
        updateBulkBar();
        document.querySelectorAll('.task-checkbox').forEach(cb => cb.checked = false);
    }
    
    function seleccionarTodas() {
        const checkboxes = document.querySelectorAll('.task-checkbox');
        checkboxes.forEach(cb => {
            cb.checked = true;
            const card = cb.closest('.task-card');
            const id = parseInt(card.id.split('-')[1]);
            selectedTaskIds.add(id);
        });
        updateBulkBar();
    }

    // ── Modal ─────────────────────────────────────────────────────
    function openModal() {
      const overlay = document.getElementById('modal-overlay');
      editingTaskId = null;
      document.getElementById('modal-title').textContent = 'Nueva tarea';
      document.getElementById('modal-subtitle').textContent = 'Anota el siguiente paso, sin complicarlo.';
      document.getElementById('btn-crear').innerHTML = `${icon('check')} Crear tarea`;
      document.querySelector('#modal-overlay .modal-mark use').setAttribute('href', '#icon-check');
      document.getElementById('m-tipo').disabled = false;
      document.getElementById('m-recurrencia').disabled = false;
      document.getElementById('repeticion-row').style.display =
        document.getElementById('m-tipo').value === 'FECHA' ? '' : 'none';
      document.querySelectorAll('#modal-overlay .task-extra-item')[1].hidden = false;
      overlay.classList.add('open');
      document.body.classList.add('task-modal-open');
      overlay.querySelector('.modal').style.transform = '';
      document.getElementById('m-titulo').focus();
      document.getElementById('err-modal').classList.remove('visible');
    }

    function openModalForDate(date, time = '23:59') {
      document.getElementById('m-tipo').value = 'FECHA';
      document.getElementById('m-fecha').value = date;
      document.getElementById('m-hora').value = time;
      toggleFecha();
      openModal();
    }

    function closeModal() {
      const overlay = document.getElementById('modal-overlay');
      overlay.classList.remove('open');
      document.body.classList.remove('task-modal-open');
      overlay.querySelector('.modal').style.transform = '';
      document.getElementById('m-titulo').value = '';
      document.getElementById('m-desc').value = '';
      document.getElementById('m-compartir').value = '';
      document.getElementById('m-tipo').value = 'SIMPLE';
      document.getElementById('m-prio').value = 'MEDIA';
      document.getElementById('m-recurrencia').value = '';
      document.getElementById('m-fecha').value = '';
      document.getElementById('m-hora').value = '23:59';
      document.getElementById('m-tipo').disabled = false;
      document.getElementById('m-recurrencia').disabled = false;
      document.getElementById('repeticion-row').style.display = 'none';
      document.querySelectorAll('#modal-overlay .task-extra-item')[1].hidden = false;
      editingTaskId = null;
      document.getElementById('fecha-row').classList.remove('visible');
      document.getElementById('repeticion-row').style.display = 'none';
      document.getElementById('subtareas-list').innerHTML = '';
      document.querySelectorAll('#modal-overlay .task-extra-item').forEach(section => {
        section.open = false;
      });
      document.getElementById('err-modal').classList.remove('visible');
    }

    function editarTarea(id) {
      const task = allTasks.find(item => item.id === id);
      if (!task || task.editable === false) {
        toast('Solo el propietario puede editar esta tarea', 'error');
        return;
      }

      document.getElementById('m-tipo').value = task.tipo;
      document.getElementById('m-prio').value = task.prioridad;
      document.getElementById('m-titulo').value = task.titulo;
      document.getElementById('m-desc').value = task.descripcion || '';
      document.getElementById('subtareas-list').innerHTML = '';
      (task.subtareas || []).forEach(subtarea => addSubtareaInput(subtarea.titulo));
      document.getElementById('m-compartir').value = '';
      toggleFecha();
      if (task.fechaLimite) {
        const deadline = new Date(task.fechaLimite);
        const localDeadline = new Date(deadline.getTime() - deadline.getTimezoneOffset() * 60000);
        document.getElementById('m-fecha').value = localDeadline.toISOString().slice(0, 10);
        document.getElementById('m-hora').value = localDeadline.toISOString().slice(11, 16);
      } else {
        document.getElementById('m-fecha').value = '';
        document.getElementById('m-hora').value = '23:59';
      }

      openModal();
      editingTaskId = id;
      document.getElementById('modal-title').textContent = 'Editar tarea';
      document.getElementById('modal-subtitle').textContent = task.recurrenciaSerieId
        ? 'Los cambios se aplicarán a esta ocurrencia y a las siguientes pendientes.'
        : 'Corrige los datos y guarda los cambios.';
      document.getElementById('btn-crear').innerHTML = `${icon('check')} Guardar cambios`;
      document.querySelector('#modal-overlay .modal-mark use').setAttribute('href', '#icon-edit');
      document.getElementById('m-recurrencia').value = '';
      document.getElementById('repeticion-row').style.display = 'none';
      document.querySelectorAll('#modal-overlay .task-extra-item')[1].hidden = true;
      document.getElementById('m-tipo').disabled = Boolean(task.recurrenciaSerieId);
      document.querySelectorAll('#modal-overlay .task-extra-item')[0].open = Boolean(task.descripcion);
      document.querySelectorAll('#modal-overlay .task-extra-item')[2].open = Boolean(task.subtareas && task.subtareas.length);
    }

    function startModalSheetDrag(event) {
      if (!window.matchMedia('(max-width: 480px)').matches) return;
      event.preventDefault();

      const modal = document.querySelector('#modal-overlay .modal');
      const startY = event.clientY;
      let offset = 0;
      modal.style.transition = 'none';

      const move = moveEvent => {
        offset = Math.max(0, moveEvent.clientY - startY);
        modal.style.transform = `translateY(${offset}px)`;
      };
      const finish = () => {
        document.removeEventListener('pointermove', move);
        document.removeEventListener('pointerup', finish);
        document.removeEventListener('pointercancel', finish);
        modal.style.removeProperty('transition');
        if (offset > 110) closeModal();
        else modal.style.removeProperty('transform');
      };

      document.addEventListener('pointermove', move);
      document.addEventListener('pointerup', finish);
      document.addEventListener('pointercancel', finish);
    }

    function toggleFecha() {
      const tipo = document.getElementById('m-tipo').value;
      document.getElementById('fecha-row').classList.toggle('visible', tipo === 'FECHA');
      document.getElementById('repeticion-row').style.display = tipo === 'FECHA' ? '' : 'none';
      if (tipo !== 'FECHA') document.getElementById('m-recurrencia').value = '';
    }

    async function doGuardarTarea() {
      const titulo = document.getElementById('m-titulo').value.trim();
      const desc = document.getElementById('m-desc').value.trim();
      const compartir = document.getElementById('m-compartir').value.trim();
      const tipo = document.getElementById('m-tipo').value;
      const prio = document.getElementById('m-prio').value;
      const fecha = document.getElementById('m-fecha').value;
      const hora = document.getElementById('m-hora').value;
      const frecuenciaRepeticion = tipo === 'FECHA' ? document.getElementById('m-recurrencia').value : '';

      if (!titulo) { showErr('err-modal', 'El título es obligatorio'); return; }
      if (tipo === 'FECHA' && (!fecha || !hora)) { showErr('err-modal', 'Selecciona la fecha y hora límite'); return; }

      const btn = document.getElementById('btn-crear');
      btn.disabled = true;
      btn.textContent = 'Guardando…';

      try {
        const subtareasInputs = document.querySelectorAll('.subtarea-input');
        const subtareas = Array.from(subtareasInputs).map(i => i.value.trim()).filter(v => v !== '').join('|');
        
        const body = { usuario: currentUser, tipo, titulo, descripcion: desc, prioridad: prio, compartirCon: compartir, subtareas };
        if (tipo === 'FECHA') {
          body.fechaLimite = new Date(`${fecha}T${hora}`).toISOString();
          if (frecuenciaRepeticion) {
            body.frecuenciaRepeticion = frecuenciaRepeticion;
            body.zonaHoraria = Intl.DateTimeFormat().resolvedOptions().timeZone;
          }
        }

        const isEditing = editingTaskId !== null;
        const taskId = editingTaskId;
        let res;
        if (isEditing && taskId < 0) {
          const queue = JSON.parse(localStorage.getItem('offlineQueue') || '[]');
          const queuedCreate = queue.find(item => item.id === -taskId && item.url === API_BASE + '/api/tareas');
          if (!queuedCreate) throw new Error('No se encontró la tarea sin sincronizar');
          const queuedBody = JSON.parse(queuedCreate.options.body);
          const queuedChanges = { ...body };
          delete queuedChanges.compartirCon;
          Object.assign(queuedBody, queuedChanges);
          if (tipo !== 'FECHA') {
            delete queuedBody.fechaLimite;
            delete queuedBody.frecuenciaRepeticion;
            delete queuedBody.zonaHoraria;
          }
          queuedCreate.options.body = JSON.stringify(queuedBody);
          localStorage.setItem('offlineQueue', JSON.stringify(queue));
          if (navigator.onLine) {
            await syncOfflineTasks();
            res = { ok: true, json: async () => ({ ok: true }) };
          } else {
            res = { ok: true, json: async () => ({ ok: true, offline: true }) };
          }
        } else {
          res = await apiFetch(
            isEditing
              ? API_BASE + `/api/tareas/${taskId}`
              : API_BASE + '/api/tareas',
            {
              method: isEditing ? 'PUT' : 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(body)
            }
          );
        }
        const data = await res.json();
        if (data.ok) {
          closeModal();
          if (isEditing && data.offline) {
            actualizarCacheTarea(taskId, body);
            saveCache();
            renderUpcomingDays();
            renderTareas();
            toast('Cambios guardados para sincronizar', 'success');
          } else if (!isEditing && data.offline) {
             const subtArr = subtareas ? subtareas.split('|').map(s => ({titulo:s, completada:false})) : [];
             allTasks.unshift({
                id: data.tempId, titulo, descripcion: desc, tipo, prioridad: prio, estado: 'PENDIENTE',
                fechaCreacion: new Date().toLocaleDateString('es-ES', {day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}), 
                fechaLimite: body.fechaLimite || null,
                recurrenciaFrecuencia: frecuenciaRepeticion || null,
                recurrenciaActiva: Boolean(frecuenciaRepeticion),
                recurrenciaPuedeDetener: Boolean(frecuenciaRepeticion),
                subtareas: subtArr
             });
             saveCache();
             renderUpcomingDays();
             renderTareas();
          } else {
             await loadTareas();
          }
          if (!isEditing) {
             toast(frecuenciaRepeticion
               ? (data.offline ? 'Tarea recurrente guardada para sincronizar' : `Serie creada: ${data.ocurrencias} tareas programadas`)
               : 'Tarea creada ✓', 'success');
          } else if (!data.offline) {
             toast('Cambios guardados', 'success');
          }
        } else {
          showErr('err-modal', data.mensaje || (isEditing ? 'No se pudieron guardar los cambios' : 'Error al crear'));
        }
      } catch (e) {
        showErr('err-modal', e.message || 'Error de conexión');
      }
      btn.disabled = false;
      btn.innerHTML = editingTaskId === null
        ? `${icon('check')} Crear tarea`
        : `${icon('check')} Guardar cambios`;
    }

    function actualizarCacheTarea(id, cambios) {
      const task = allTasks.find(item => item.id === id);
      if (!task) return;
      const originalDeadline = task.fechaLimite ? new Date(task.fechaLimite) : null;
      const newDeadline = cambios.fechaLimite ? new Date(cambios.fechaLimite) : null;
      const localTimestamp = date => Date.UTC(
        date.getFullYear(), date.getMonth(), date.getDate(),
        date.getHours(), date.getMinutes(), date.getSeconds(), date.getMilliseconds()
      );
      const shift = originalDeadline && newDeadline
        ? localTimestamp(newDeadline) - localTimestamp(originalDeadline)
        : 0;
      const shiftLocalDate = date => {
        const shifted = new Date(localTimestamp(date) + shift);
        return new Date(
          shifted.getUTCFullYear(), shifted.getUTCMonth(), shifted.getUTCDate(),
          shifted.getUTCHours(), shifted.getUTCMinutes(), shifted.getUTCSeconds(),
          shifted.getUTCMilliseconds()
        );
      };
      const serieId = task.recurrenciaSerieId;
      const updates = allTasks.filter(item => item.id === id || (
        serieId && item.recurrenciaSerieId === serieId
        && item.recurrenciaIndice >= task.recurrenciaIndice
        && item.estado !== 'COMPLETADA'
      ));

      updates.forEach(item => {
        const dueDate = item.id === id
          ? newDeadline
          : item.fechaLimite && shift ? shiftLocalDate(new Date(item.fechaLimite)) : null;
        item.titulo = cambios.titulo;
        item.descripcion = cambios.descripcion;
        item.prioridad = cambios.prioridad;
        item.tipo = cambios.tipo;
        item.fechaLimite = dueDate ? dueDate.toISOString() : null;
        const titles = cambios.subtareas ? cambios.subtareas.split('|').filter(Boolean) : [];
        const previous = item.subtareas || [];
        const used = new Set();
        item.subtareas = titles.map(title => {
          const matchIndex = previous.findIndex((subtask, index) => !used.has(index) && subtask.titulo === title);
          if (matchIndex >= 0) used.add(matchIndex);
          return { titulo: title, completada: matchIndex >= 0 && previous[matchIndex].completada };
        });
      });
    }

    // ── Calendar ──────────────────────────────────────────────────
    const MONTHS_ES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
      'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

    function setCalendarMode(mode) {
      calendarMode = mode;
      document.querySelectorAll('.cal-view-btn').forEach(button => {
        const selected = button.dataset.calendarMode === mode;
        button.classList.toggle('active', selected);
        button.setAttribute('aria-pressed', selected);
      });
      renderCalendar();
    }

    function navigateCalendar(direction) {
      if (calendarMode === 'month') {
        const year = calendarDate.getFullYear();
        const month = calendarDate.getMonth() + direction;
        const day = calendarDate.getDate();
        calendarDate = new Date(year, month, Math.min(day, new Date(year, month + 1, 0).getDate()));
      } else {
        const days = calendarMode === 'week' ? 7 : 1;
        calendarDate.setDate(calendarDate.getDate() + direction * days);
      }
      renderCalendar();
    }

    function startOfCalendarWeek(date) {
      const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
      start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
      return start;
    }

    function parseDeadline(value) {
      if (!value) return null;
      const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
      if (dateOnly) {
        return new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]), 23, 59);
      }
      const date = new Date(value);
      return Number.isNaN(date.getTime()) ? null : date;
    }

    function deadlineDateKey(value) {
      const date = parseDeadline(value);
      return date ? isoDate(date.getFullYear(), date.getMonth(), date.getDate()) : null;
    }

    function getDaysUntilDeadline(value) {
      const deadline = parseDeadline(value);
      if (!deadline) return 0;
      const now = new Date();
      const todayUtc = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
      const deadlineUtc = Date.UTC(deadline.getFullYear(), deadline.getMonth(), deadline.getDate());
      return Math.round((deadlineUtc - todayUtc) / 86400000);
    }

    function formatDeadline(value) {
      const date = parseDeadline(value);
      return date ? date.toLocaleString('es-ES', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
      }) : value;
    }

    function formatDeadlineTime(value) {
      const date = parseDeadline(value);
      return date ? date.toLocaleTimeString('es-ES', {
        hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
      }) : '';
    }

    function renderCalendar() {
      const calYear = calendarDate.getFullYear();
      const calMonth = calendarDate.getMonth();
      const grid = document.getElementById('cal-grid');
      const weekdays = document.getElementById('cal-weekdays');
      grid.innerHTML = '';
      grid.className = `cal-grid cal-grid-${calendarMode}`;
      weekdays.hidden = calendarMode !== 'month';

      // Build a map: 'YYYY-MM-DD' → [task, ...]
      const taskMap = {};
      allTasks.forEach(t => {
        if (t.fechaLimite) {
          const dateKey = deadlineDateKey(t.fechaLimite);
          if (dateKey) {
            taskMap[dateKey] = taskMap[dateKey] || [];
            taskMap[dateKey].push(t);
          }
        }
      });
      Object.values(taskMap).forEach(tasks => {
        tasks.sort((a, b) => parseDeadline(a.fechaLimite) - parseDeadline(b.fechaLimite));
      });

      const today = new Date();
      const todayStr = isoDate(today.getFullYear(), today.getMonth(), today.getDate());

      if (calendarMode === 'week') {
        const weekStart = startOfCalendarWeek(calendarDate);
        const weekEnd = new Date(weekStart.getFullYear(), weekStart.getMonth(), weekStart.getDate() + 6);
        const formatDate = date => date.toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
        document.getElementById('cal-title').textContent = `${formatDate(weekStart)} – ${formatDate(weekEnd)}`;
        for (let i = 0; i < 7; i++) {
          const date = new Date(weekStart.getFullYear(), weekStart.getMonth(), weekStart.getDate() + i);
          appendCalendarDay(grid, date, taskMap, todayStr, false, true);
        }
        return;
      }

      if (calendarMode === 'day') {
        document.getElementById('cal-title').textContent = calendarDate.toLocaleDateString('es-ES', {
          weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
        });
        appendCalendarDayTimeline(grid, calendarDate, taskMap);
        return;
      }

      document.getElementById('cal-title').textContent = `${MONTHS_ES[calMonth]} ${calYear}`;

      // First day of the month (week starts on Monday)
      const firstDay = new Date(calYear, calMonth, 1);
      let startDow = firstDay.getDay(); // 0=Sun
      startDow = (startDow === 0) ? 6 : startDow - 1; // shift: Mon=0 … Sun=6

      const daysInMonth = new Date(calYear, calMonth + 1, 0).getDate();
      const daysInPrev = new Date(calYear, calMonth, 0).getDate();
      const totalCells = Math.ceil((startDow + daysInMonth) / 7) * 7;

      for (let i = 0; i < totalCells; i++) {
        let day, month, year, otherMonth = false;
        if (i < startDow) {
          // Previous month fill
          day = daysInPrev - startDow + 1 + i;
          month = calMonth - 1; year = calYear;
          if (month < 0) { month = 11; year--; }
          otherMonth = true;
        } else if (i >= startDow + daysInMonth) {
          // Next month fill
          day = i - startDow - daysInMonth + 1;
          month = calMonth + 1; year = calYear;
          if (month > 11) { month = 0; year++; }
          otherMonth = true;
        } else {
          day = i - startDow + 1;
          month = calMonth; year = calYear;
        }

        appendCalendarDay(grid, new Date(year, month, day), taskMap, todayStr, otherMonth);
      }
    }

    function appendCalendarDay(grid, date, taskMap, todayStr, otherMonth, showWeekday = false) {
      const dateStr = isoDate(date.getFullYear(), date.getMonth(), date.getDate());
      const tasks = taskMap[dateStr] || [];
      const cell = document.createElement('div');
      cell.className = 'cal-cell'
        + (otherMonth ? ' other-month' : '')
        + (dateStr === todayStr ? ' today' : '')
        + (tasks.length ? ' has-tasks' : '');
      cell.dataset.date = dateStr;
      cell.setAttribute('role', 'button');
      cell.tabIndex = 0;
      cell.setAttribute('aria-label', `Crear tarea para el ${date.toLocaleDateString('es-ES', {
        weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
      })}`);
      cell.addEventListener('click', () => openModalForDate(dateStr));
      cell.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          openModalForDate(dateStr);
        }
      });
      enableCalendarDrop(cell);

      const numEl = document.createElement('div');
      numEl.className = 'cal-day-num';
      numEl.textContent = showWeekday
        ? date.toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric' })
        : date.getDate();
      cell.appendChild(numEl);

      const compactMonth = calendarMode === 'month';
      const visibleTasks = compactMonth ? tasks.slice(0, 1) : tasks;
      visibleTasks.forEach(t => {
        cell.appendChild(createCalendarPill(t));
      });

      if (compactMonth && tasks.length > 1) {
        const more = document.createElement('button');
        more.className = 'cal-more-count';
        more.type = 'button';
        more.textContent = `+${tasks.length - 1}`;
        more.title = `Mostrar ${tasks.length - 1} tareas más`;
        more.setAttribute('aria-label', `Mostrar ${tasks.length - 1} tareas más`);
        more.setAttribute('aria-expanded', 'false');
        enableMoreTasksPopover(more, tasks.slice(1));
        cell.appendChild(more);
      }

      grid.appendChild(cell);
    }

    function appendCalendarDayTimeline(grid, date, taskMap) {
      const dateKey = isoDate(date.getFullYear(), date.getMonth(), date.getDate());
      const timeline = document.createElement('div');
      timeline.className = 'cal-day-timeline';
      const tasks = taskMap[dateKey] || [];

      for (let hour = 0; hour < 24; hour++) {
        const hourText = `${String(hour).padStart(2, '0')}:00`;
        const row = document.createElement('div');
        row.className = 'cal-hour-row';
        row.dataset.date = dateKey;
        row.dataset.hour = String(hour);
        row.setAttribute('role', 'button');
        row.tabIndex = 0;
        row.setAttribute('aria-label', `Crear tarea el ${date.toLocaleDateString('es-ES')} a las ${hourText}`);
        row.addEventListener('click', () => openModalForDate(dateKey, hourText));
        row.addEventListener('keydown', event => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            openModalForDate(dateKey, hourText);
          }
        });
        enableCalendarDrop(row);

        const label = document.createElement('div');
        label.className = 'cal-hour-label';
        label.textContent = hourText;
        row.appendChild(label);

        const taskContainer = document.createElement('div');
        taskContainer.className = 'cal-hour-tasks';
        tasks.filter(task => parseDeadline(task.fechaLimite).getHours() === hour)
          .forEach(task => taskContainer.appendChild(createCalendarPill(task)));
        row.appendChild(taskContainer);
        timeline.appendChild(row);
      }

      grid.appendChild(timeline);
    }

    function enableCalendarDrop(target) {
      target.addEventListener('dragover', event => {
        if (draggedTaskId === null) return;
        event.preventDefault();
        target.classList.add('drag-over');
      });
      target.addEventListener('dragleave', event => {
        if (!target.contains(event.relatedTarget)) target.classList.remove('drag-over');
      });
      target.addEventListener('drop', event => {
        event.preventDefault();
        target.classList.remove('drag-over');
        const taskId = Number(event.dataTransfer.getData('text/plain')) || draggedTaskId;
        moveTaskDeadline(taskId, target.dataset.date, target.dataset.hour);
      });
    }

    function enableTouchCalendarDrag(pill, task) {
      pill.addEventListener('pointerdown', event => {
        if (event.pointerType !== 'touch') return;

        const startX = event.clientX;
        const startY = event.clientY;
        const dayScroller = pill.closest('.upcoming-days');
        let isDragging = false;
        let dropTarget = null;
        let lastPointer = null;
        let autoScrollFrame = null;
        const timer = setTimeout(() => {
          isDragging = true;
          draggedTaskId = task.id;
          pill.classList.add('dragging');
          if (dayScroller) dayScroller.classList.add('task-dragging');
        }, 450);

        const clearDropTarget = () => {
          if (dropTarget) dropTarget.classList.remove('drag-over');
          dropTarget = null;
        };
        const stopAutoScroll = () => {
          if (autoScrollFrame !== null) cancelAnimationFrame(autoScrollFrame);
          autoScrollFrame = null;
        };
        const updateDropTarget = () => {
          const target = lastPointer
            ? document.elementFromPoint(lastPointer.x, lastPointer.y)
              ?.closest('.cal-cell, .cal-hour-row, .upcoming-day')
            : null;
          if (target === dropTarget) return;
          clearDropTarget();
          dropTarget = target;
          if (dropTarget) dropTarget.classList.add('drag-over');
        };
        const autoScroll = () => {
          autoScrollFrame = null;
          if (!isDragging || !dayScroller || !lastPointer) return;
          const bounds = dayScroller.getBoundingClientRect();
          const edge = 40;
          const maxScroll = dayScroller.scrollWidth - dayScroller.clientWidth;
          const direction = lastPointer.x <= bounds.left + edge && dayScroller.scrollLeft > 0
            ? -1
            : lastPointer.x >= bounds.right - edge && dayScroller.scrollLeft < maxScroll
              ? 1
              : 0;
          if (!direction) return;
          dayScroller.scrollLeft += direction * 12;
          updateDropTarget();
          autoScrollFrame = requestAnimationFrame(autoScroll);
        };
        const finish = upEvent => {
          clearTimeout(timer);
          stopAutoScroll();
          document.removeEventListener('pointermove', move);
          document.removeEventListener('pointerup', finish);
          document.removeEventListener('pointercancel', cancel);
          if (isDragging) {
            const target = document.elementFromPoint(upEvent.clientX, upEvent.clientY)
              ?.closest('.cal-cell, .cal-hour-row, .upcoming-day');
            clearDropTarget();
            pill.classList.remove('dragging');
            if (dayScroller) dayScroller.classList.remove('task-dragging');
            draggedTaskId = null;
            if (target) moveTaskDeadline(task.id, target.dataset.date, target.dataset.hour);
            upEvent.preventDefault();
          }
        };
        const cancel = () => {
          clearTimeout(timer);
          stopAutoScroll();
          clearDropTarget();
          pill.classList.remove('dragging');
          if (dayScroller) dayScroller.classList.remove('task-dragging');
          draggedTaskId = null;
          document.removeEventListener('pointermove', move);
          document.removeEventListener('pointerup', finish);
          document.removeEventListener('pointercancel', cancel);
        };
        const move = moveEvent => {
          if (!isDragging) {
            if (Math.hypot(moveEvent.clientX - startX, moveEvent.clientY - startY) > 10) clearTimeout(timer);
            return;
          }
          moveEvent.preventDefault();
          lastPointer = { x: moveEvent.clientX, y: moveEvent.clientY };
          updateDropTarget();
          if (dayScroller) {
            const bounds = dayScroller.getBoundingClientRect();
            const maxScroll = dayScroller.scrollWidth - dayScroller.clientWidth;
            const atScrollableEdge = (
              lastPointer.x <= bounds.left + 40 && dayScroller.scrollLeft > 0
            ) || (
              lastPointer.x >= bounds.right - 40 && dayScroller.scrollLeft < maxScroll
            );
            if (atScrollableEdge && autoScrollFrame === null) {
              autoScrollFrame = requestAnimationFrame(autoScroll);
            } else if (!atScrollableEdge) {
              stopAutoScroll();
            }
          }
        };

        document.addEventListener('pointermove', move, { passive: false });
        document.addEventListener('pointerup', finish);
        document.addEventListener('pointercancel', cancel);
      });
    }

    async function moveTaskDeadline(taskId, dateKey, hour) {
      if (!dateKey) return;
      const task = allTasks.find(item => item.id === taskId);
      if (!task || !task.fechaLimite) return;
      const currentDate = parseDeadline(task.fechaLimite);
      if (!currentDate) return;

      const [year, month, day] = dateKey.split('-').map(Number);
      const targetDate = new Date(
        year,
        month - 1,
        day,
        hour === undefined ? currentDate.getHours() : Number(hour),
        hour === undefined ? currentDate.getMinutes() : 0,
        hour === undefined ? currentDate.getSeconds() : 0,
        hour === undefined ? currentDate.getMilliseconds() : 0
      );
      if (targetDate.getTime() === currentDate.getTime()) return;

      dismissPopover();
      const previousDeadline = task.fechaLimite;
      taskMutationVersion++;
      pendingDeadlineWrites++;
      task.fechaLimite = targetDate.toISOString();
      saveCache();
      renderTaskSnapshot();

      try {
        if (taskId < 0) {
          const queue = JSON.parse(localStorage.getItem('offlineQueue') || '[]');
          const queuedCreate = queue.find(item => item.id === -taskId && item.url === API_BASE + '/api/tareas');
          if (!queuedCreate) throw new Error('No se pudo encontrar la tarea pendiente de sincronización');
          const queuedBody = JSON.parse(queuedCreate.options.body);
          queuedBody.fechaLimite = task.fechaLimite;
          queuedCreate.options.body = JSON.stringify(queuedBody);
          localStorage.setItem('offlineQueue', JSON.stringify(queue));
          toast('Fecha actualizada; la tarea se sincronizará al reconectar', 'success');
          return;
        }

        const res = await apiFetch(API_BASE + `/api/tareas/${taskId}/fecha`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ usuario: currentUser, fechaLimite: task.fechaLimite })
        });
        const data = await res.json();
        if (!res.ok || !data.ok) throw new Error(data.mensaje || 'No se pudo actualizar la fecha');
        toast(data.offline ? 'Cambio guardado; se sincronizará al reconectar' : 'Fecha límite actualizada', 'success');
      } catch (e) {
        task.fechaLimite = previousDeadline;
        saveCache();
        renderTaskSnapshot();
        toast(e.message || 'No se pudo actualizar la fecha límite', 'error');
      } finally {
        pendingDeadlineWrites--;
      }
    }

    function enableCalendarTaskInteractions(element, task, showDetails = true) {
      element.draggable = true;
      element.setAttribute('aria-label', `${task.titulo}. Arrastra para cambiar la fecha límite.`);
      element.addEventListener('dragstart', event => {
        draggedTaskId = task.id;
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', String(task.id));
        element.classList.add('dragging');
      });
      element.addEventListener('dragend', () => {
        draggedTaskId = null;
        element.classList.remove('dragging');
        document.querySelectorAll('.drag-over').forEach(target => target.classList.remove('drag-over'));
        if (popoverAnchor?.matches('.upcoming-more-count, .cal-more-count')) {
          schedulePopoverDismiss();
        }
      });
      enableTouchCalendarDrag(element, task);
      element.addEventListener('click', event => event.stopPropagation());
      if (showDetails) {
        element.addEventListener('mouseenter', event => showPopover(event, task));
        element.addEventListener('mouseleave', dismissPopover);
      }
    }

    function createCalendarPill(task) {
      const pill = document.createElement('div');
      const prio = task.prioridad.toLowerCase();
      const done = task.estado === 'COMPLETADA';
      pill.className = `cal-pill ${prio}${done ? ' completada' : ''}`;
      pill.title = task.titulo;
      enableCalendarTaskInteractions(pill, task);

      const title = document.createElement('span');
      title.className = 'cal-pill-title';
      title.textContent = task.titulo;
      pill.appendChild(title);

      const time = formatDeadlineTime(task.fechaLimite);
      if (time) {
        const timeLabel = document.createElement('span');
        timeLabel.className = 'cal-pill-time';
        timeLabel.textContent = time;
        pill.appendChild(timeLabel);
      }

      return pill;
    }

    function isoDate(y, m, d) {
      return `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    }

    function enableMoreTasksPopover(button, tasks) {
      const supportsHover = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
      button.addEventListener('mouseenter', event => {
        if (supportsHover) {
          cancelPopoverDismiss();
          showMoreTasksPopover(tasks, button);
        }
      });
      button.addEventListener('mouseleave', () => {
        if (supportsHover && popoverAnchor === button) schedulePopoverDismiss();
      });
      button.addEventListener('click', event => {
        event.stopPropagation();
        if (supportsHover && event.detail !== 0) return;
        if (popoverAnchor === button) dismissPopover();
        else showMoreTasksPopover(tasks, button);
      });
    }

    function showMoreTasksPopover(tasks, anchor) {
      dismissPopover();
      const pop = document.createElement('div');
      pop.className = 'cal-popover cal-more-popover';
      pop.addEventListener('mouseenter', cancelPopoverDismiss);
      pop.addEventListener('mouseleave', schedulePopoverDismiss);
      const heading = document.createElement('strong');
      heading.textContent = `${tasks.length} tareas más`;
      pop.appendChild(heading);

      const stateLabels = { PENDIENTE: 'Pendiente', EN_PROGRESO: 'En progreso', COMPLETADA: 'Completada' };
      const priorityLabels = { ALTA: 'Alta', MEDIA: 'Media', BAJA: 'Baja' };
      const list = document.createElement('ul');
      list.className = 'cal-more-list';
      tasks.forEach(task => {
        const item = document.createElement('li');
        item.className = `cal-more-item ${String(task.prioridad || '').toLowerCase()}`;
        item.dataset.taskId = String(task.id);
        const title = document.createElement('span');
        title.className = 'cal-more-task-title';
        title.textContent = task.titulo;
        const meta = document.createElement('span');
        meta.className = 'cal-more-task-meta';
        meta.textContent = [
          formatDeadlineTime(task.fechaLimite),
          stateLabels[task.estado] || task.estado,
          priorityLabels[task.prioridad] || task.prioridad
        ].filter(Boolean).join(' · ');
        item.append(title, meta);
        enableCalendarTaskInteractions(item, task, false);
        list.appendChild(item);
      });
      pop.appendChild(list);
      document.body.appendChild(pop);
      popoverEl = pop;
      popoverAnchor = anchor;
      anchor.setAttribute('aria-expanded', 'true');
      positionPopover(pop, anchor);
    }

    function showPopover(e, t) {
      dismissPopover();
      const pop = document.createElement('div');
      pop.className = 'cal-popover';
      const stateLabel = { PENDIENTE: 'Pendiente', EN_PROGRESO: 'En progreso', COMPLETADA: 'Completada' };
      const prioLabel = { ALTA: 'Alta', MEDIA: 'Media', BAJA: 'Baja' };
      pop.innerHTML = `
    <strong>${escHtml(t.titulo)}</strong>
    <span>${prioLabel[t.prioridad] || t.prioridad}</span>
    <span>${stateLabel[t.estado] || t.estado}</span>
    <span>${icon('calendar-date')} Límite: ${formatDeadline(t.fechaLimite)}</span>
    ${t.descripcion ? `<span style="margin-top:.3rem;color:var(--text);font-size:.75rem">${escHtml(t.descripcion)}</span>` : ''}
  `;
      document.body.appendChild(pop);
      popoverEl = pop;
  popoverAnchor = e.currentTarget || e.target;
  positionPopover(pop, popoverAnchor);
    }

    function positionPopover(pop, anchor) {
  const rect = anchor.getBoundingClientRect();
      const margin = 12;
      const maxLeft = Math.max(margin, window.innerWidth - pop.offsetWidth - margin);
      const maxTop = Math.max(margin, window.innerHeight - pop.offsetHeight - margin);
      let left = rect.right + 8;
      let top = rect.top;
      if (left + pop.offsetWidth > window.innerWidth - margin) left = rect.left - pop.offsetWidth - 8;
      left = Math.min(Math.max(left, margin), maxLeft);
      top = Math.min(Math.max(top, margin), maxTop);
      pop.style.left = left + 'px';
      pop.style.top = top + 'px';
    }

    function schedulePopoverDismiss() {
      cancelPopoverDismiss();
      popoverDismissTimer = setTimeout(() => {
        popoverDismissTimer = null;
        if (draggedTaskId === null) dismissPopover();
      }, 180);
    }

    function cancelPopoverDismiss() {
      if (popoverDismissTimer !== null) clearTimeout(popoverDismissTimer);
      popoverDismissTimer = null;
    }

    function dismissPopover() {
      cancelPopoverDismiss();
      if (popoverEl) { popoverEl.remove(); popoverEl = null; }
      if (popoverAnchor?.matches('.upcoming-more-count, .cal-more-count')) {
        popoverAnchor.setAttribute('aria-expanded', 'false');
      }
      popoverAnchor = null;
    }

    // ── Ruleta ───────────────────────────────────────────────────
    function tirarRuleta() {
      const tasks = allTasks.filter(t => t.estado !== 'COMPLETADA');
      const resultDiv = document.getElementById('ruleta-result');
      const btn = document.getElementById('btn-spin');
      
      if (tasks.length === 0) {
        resultDiv.innerHTML = `<div class="empty-state" style="padding:2rem 0">${icon('check', 'empty-icon')}<p>¡No tienes tareas pendientes! Tómate un descanso.</p></div>`;
        return;
      }

      btn.disabled = true;
      let spins = 0;
      const maxSpins = 15 + Math.floor(Math.random() * 10);
      
      const doSpin = () => {
        const randomTask = tasks[Math.floor(Math.random() * tasks.length)];
        resultDiv.innerHTML = taskCardHTML(randomTask);
        spins++;
        
        if (spins < maxSpins) {
           const delay = 50 + (spins * spins * 0.5); 
           setTimeout(doSpin, delay);
        } else {
           btn.disabled = false;
        }
      };
      doSpin();
    }

    // ── Utility ───────────────────────────────────────────────────
    function showErr(id, msg) {
      const el = document.getElementById(id);
      el.textContent = msg;
      el.classList.add('visible');
    }

    function setBtnLoading(btnId, labelId, loading, resetText) {
      const btn = document.getElementById(btnId);
      const label = document.getElementById(labelId);
      btn.disabled = loading;
      label.innerHTML = loading ? '<span class="spinner"></span>' : resetText;
    }

    function escHtml(s) {
      return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function toast(msg, type = 'success') {
      const c = document.getElementById('toast-container');
      const t = document.createElement('div');
      t.className = `toast ${type}`;
      t.textContent = msg;
      c.appendChild(t);
      setTimeout(() => t.remove(), 3000);
    }

    // ── Exportación ────────────────────────────────────────────────
    function exportarCSV() {
      if (allTasks.length === 0) {
        toast('No hay tareas para exportar', 'error');
        return;
      }
      
      let csvContent = `data:text/csv;charset=utf-8,\n`;
      csvContent += "ID;Titulo;Tipo;Prioridad;Estado;Creacion;Limite;Subtareas\n";

      allTasks.forEach(t => {
        const title = t.titulo.replace(/;/g, ',');
        const subt = t.subtareas ? `${t.subtareas.filter(s => s.completada).length}/${t.subtareas.length}` : '0/0';
        const limit = t.fechaLimite ? formatDeadline(t.fechaLimite) : '';
        
        let row = `${t.id};"${title}";${t.tipo};${t.prioridad};${t.estado};${t.fechaCreacion};${limit};${subt}`;
        csvContent += row + "\n";
      });

      const encodedUri = encodeURI(csvContent);
      const link = document.createElement("a");
      link.setAttribute("href", encodedUri);
      link.setAttribute("download", `tareas_${currentUser}_${new Date().getTime()}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      toast('Exportando CSV...', 'success');
    }

    function exportarPDF() {
      if (allTasks.length === 0) {
        toast('No hay tareas para exportar', 'error');
        return;
      }

      toast('Preparando PDF...', 'success');
      const win = window.open('', '_blank');
      let html = `
        <html>
        <head>
          <title>Reporte de Tareas - ${currentUser}</title>
          <style>
            body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #26332e; line-height: 1.5; padding: 2rem; max-width: 800px; margin: auto; }
            h1 { color: #52796f; border-bottom: 2px solid #e3e9e4; padding-bottom: .5rem; }
            table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
            th, td { border: 1px solid #dfe6e0; padding: .5rem; text-align: left; }
            th { background-color: #f1f4f1; }
            .badge { font-size: .8rem; padding: 2px 6px; border-radius: 4px; background: #f1f4f1; }
            @media print {
              body { padding: 0; }
              button { display: none; }
            }
          </style>
        </head>
        <body>
          <h1>Resumen de Tareas de ${currentUser}</h1>
          <p>Generado el: ${new Date().toLocaleString()}</p>
          <button onclick="window.print()" style="padding: 10px 20px; background: #52796f; color: white; border: none; border-radius: 5px; cursor: pointer; margin-bottom: 20px;">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9V2h12v7M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><path d="M6 14h12v8H6z"/></svg>
            Imprimir / Guardar como PDF
          </button>
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Título</th>
                <th>Estado</th>
                <th>Prioridad</th>
                <th>Creada</th>
                <th>Límite</th>
              </tr>
            </thead>
            <tbody>
      `;
      
      allTasks.forEach(t => {
        const st = t.subtareas && t.subtareas.length > 0 ? `<br><small>${t.subtareas.filter(s=>s.completada).length}/${t.subtareas.length} pasos completados</small>` : '';
        html += `
          <tr>
            <td>${t.id}</td>
            <td><strong>${escHtml(t.titulo)}</strong>${st}</td>
            <td>${t.estado}</td>
            <td>${t.prioridad}</td>
            <td>${t.fechaCreacion.split(' ')[0]}</td>
            <td>${t.fechaLimite ? formatDeadline(t.fechaLimite) : '-'}</td>
          </tr>
        `;
      });

      html += `
            </tbody>
          </table>
          <script>
            window.onload = () => { setTimeout(() => window.print(), 500); }
          <\/script>
        </body>
        </html>
      `;
      win.document.write(html);
      win.document.close();
    }

    // ── Keyboard shortcuts ────────────────────────────────────────
    document.getElementById('modal-overlay').addEventListener('click', event => {
      if (event.target === event.currentTarget) closeModal();
    });

    document.addEventListener('keydown', e => {
      if (e.key === 'Escape' && document.getElementById('user-menu').classList.contains('open')) {
        toggleUserMenu(true);
        document.getElementById('user-menu-trigger').focus();
      }
      if (e.key === 'Escape' && document.getElementById('filters-disclosure').open) {
        document.getElementById('filters-disclosure').open = false;
        document.querySelector('.filters-summary').focus();
      }
      if (e.key === 'Escape' && document.getElementById('view-actions').open) {
        closeViewActions();
        document.querySelector('.view-actions-summary').focus();
      }
      if (e.key === 'Escape' && document.getElementById('header-view-menu').open) {
        document.getElementById('header-view-menu').open = false;
        document.getElementById('header-view-summary').focus();
      }
      if (e.key === 'Enter') {
        if (document.getElementById('panel-login').style.display !== 'none'
          && document.getElementById('auth-page').style.display !== 'none') doLogin();
        else if (document.getElementById('panel-registro').style.display !== 'none'
          && document.getElementById('auth-page').style.display !== 'none') doRegistro();
      }
      if (e.key === 'Escape' && document.getElementById('modal-overlay').classList.contains('open')) closeModal();
      if (e.key === 'Escape' && document.getElementById('groups-modal-overlay').classList.contains('open')) closeGroupsModal();
      if (e.key === 'Escape' && document.getElementById('share-modal-overlay').classList.contains('open')) closeShareModal();
    });

    // ── Restaurar sesión automáticamente al recargar la página ───
    const savedUser = localStorage.getItem('taskdle_user');
    if (savedUser) {
      currentUser = savedUser;
      enterDashboard();
    }
