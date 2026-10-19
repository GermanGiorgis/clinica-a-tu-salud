# Imagen del frontend en dos etapas: se compila con Node y se sirve con nginx (solo archivos estáticos).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# La dirección del backend se fija al compilar (Vite la incrusta en el código).
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
# VITE_MODO_DEMO=true muestra el aviso de demostración y las cuentas de prueba en el login.
ARG VITE_MODO_DEMO=false
ENV VITE_MODO_DEMO=$VITE_MODO_DEMO
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
