# Taskdle — Guía de Despliegue

## Arquitectura

```
┌─────────────────────────────────────────────┐
│  Usuario (Navegador)                        │
└──────────────┬──────────────────────────────┘
               │ HTTPS
┌──────────────▼──────────────────────────────┐
│  Vercel (Frontend estático)                 │
│  frontend/public/index.html                 │
│  → taskdle.vercel.app                       │
└──────────────┬──────────────────────────────┘
               │ HTTPS / REST API
┌──────────────▼──────────────────────────────┐
│  Render (Backend Java)                      │
│  backend/ → Docker JAR                      │
│  → taskdle-backend.onrender.com             │
└──────────────┬──────────────────────────────┘
               │ JDBC / SSL
┌──────────────▼──────────────────────────────┐
│  Supabase (PostgreSQL)                      │
│  Tablas: usuarios, tareas, subtareas        │
└─────────────────────────────────────────────┘
```

---

## 1. Configurar Supabase

1. Ve a [supabase.com](https://supabase.com) → **New project**
2. Espera a que se cree la BD
3. Ve a **SQL Editor** y ejecuta el contenido de `backend/src/main/resources/schema.sql`
4. Ve a **Project Settings → Database → Connection string → URI**
5. Copia la URI (formato: `postgresql://postgres:[PASSWORD]@db.[REF].supabase.co:5432/postgres`)

---

## 2. Desplegar Backend en Render

1. Ve a [render.com](https://render.com) → **New → Web Service**
2. Conecta tu repositorio de GitHub
3. Configuración:
   - **Build Command**: *(usa Docker, ya detecta el Dockerfile)*
   - **Dockerfile Path**: `backend/Dockerfile`
4. En **Environment Variables** añade:
   ```
   DATABASE_URL = postgresql://postgres:[PASSWORD]@db.[REF].supabase.co:5432/postgres
   ```
5. Haz clic en **Create Web Service**
6. Copia la URL que te da Render (ej: `https://taskdle-backend.onrender.com`)

> **Nota**: El plan gratuito de Render "duerme" tras 15 min de inactividad. El primer request puede tardar ~30s.

---

## 3. Actualizar la URL del Backend en el Frontend

Edita `frontend/public/index.html` y busca esta línea:

```javascript
: 'https://taskdle-backend.onrender.com');  // ← Actualizar con tu URL de Render
```

Reemplaza `taskdle-backend.onrender.com` con la URL real de tu servicio en Render.

---

## 4. Desplegar Frontend en Vercel

1. Ve a [vercel.com](https://vercel.com) → **New Project**
2. Importa tu repositorio
3. Configuración:
   - **Framework Preset**: Other
   - **Root Directory**: `frontend`
   - **Build Command**: *(dejar vacío)*
   - **Output Directory**: `public`
4. Haz clic en **Deploy**
5. Tu app estará en `https://taskdle.vercel.app`

---

## 5. Desarrollo Local

```bash
# Backend (requiere Java 17 y Gradle)
cd backend
cp .env.example .env
# Edita .env con tu DATABASE_URL de Supabase
gradle fatJar --no-daemon
java -Ddatabase.url=$DATABASE_URL -jar build/libs/taskdle-all.jar

# Frontend
# Simplemente abre frontend/public/index.html en el navegador
# O usa un servidor estático:
cd frontend/public && python -m http.server 3000
```

---

## Estructura del Proyecto

```
ia_proy/
├── backend/                    → Render (Java)
│   ├── src/main/java/com/taskdle/
│   │   ├── Main.java
│   │   ├── api/ServidorWeb.java
│   │   ├── service/GestorUsuarios.java
│   │   ├── db/
│   │   │   ├── Database.java
│   │   │   ├── UsuarioDao.java
│   │   │   └── TareaDao.java
│   │   └── model/
│   │       ├── Tarea.java (abstract)
│   │       ├── TareaSimple.java
│   │       ├── TareaConFechaLimite.java
│   │       ├── Subtarea.java
│   │       ├── Usuario.java
│   │       ├── Estado.java
│   │       └── Prioridad.java
│   ├── src/main/resources/schema.sql
│   ├── build.gradle
│   ├── Dockerfile
│   └── .env.example
├── frontend/                   → Vercel (HTML estático)
│   ├── public/
│   │   ├── index.html
│   │   ├── icon.svg
│   │   ├── manifest.json
│   │   └── sw.js
│   └── vercel.json
├── render.yaml                 → Config de Render
└── DEPLOY.md                   → Este archivo
```
