# --- ETAPA 1: DESARROLLO (Node + Angular Live Reload) ---
FROM node:22-alpine AS dev
WORKDIR /app

COPY package*.json ./
RUN npm install

EXPOSE 4200
CMD ["npm", "run", "start", "--", "--host", "0.0.0.0", "--poll", "2000"]

# --- ETAPA 2: BUILD DE PRODUCCIÓN ---
FROM dev AS build
COPY . ./
RUN npm run build

# --- ETAPA 3: PRODUCCIÓN (Nginx) ---
FROM nginx:alpine AS prod
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]