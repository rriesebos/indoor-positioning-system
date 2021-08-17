FROM node:15-alpine as base

# Setting the working directory as /app
WORKDIR /app

# Copying package.json and package-lock.json to Docker Image
COPY package.json ./
COPY package-lock.json ./

RUN apk add --no-cache bash

EXPOSE 3000

FROM base as production
ENV NODE_ENV=production
RUN npm ci
COPY . .
CMD ["node", "bin/www"]

FROM base as development
ENV NODE_ENV=development
RUN npm install -g nodemon && npm install
COPY . .
CMD ["nodemon", "bin/www"]
