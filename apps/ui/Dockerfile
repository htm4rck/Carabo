FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --prefer-offline

FROM node:22-alpine AS build
WORKDIR /app
ARG CARABO_UI_VERSION=0.2.0
ARG CARABO_BUILD_DATE=unknown
ARG CARABO_COMMIT=unknown
ENV CARABO_UI_VERSION=$CARABO_UI_VERSION \
    CARABO_BUILD_DATE=$CARABO_BUILD_DATE \
    CARABO_COMMIT=$CARABO_COMMIT
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build:prod

FROM nginx:alpine
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist/carabo-ui/browser /usr/share/nginx/html
RUN if [ -f /usr/share/nginx/html/index.csr.html ]; then \
      mv /usr/share/nginx/html/index.csr.html /usr/share/nginx/html/index.html; \
    fi
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
