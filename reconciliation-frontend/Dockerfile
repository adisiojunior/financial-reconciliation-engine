# Estágio 1: Build da aplicação Angular
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build --configuration=production

# Estágio 2: Servidor Web de produção com Nginx
FROM nginx:alpine
COPY --from=build /app/dist/reconciliation-frontend /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]