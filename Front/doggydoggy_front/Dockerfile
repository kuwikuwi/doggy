FROM node:16 as build-stage
WORKDIR /src
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build
CMD ["npm", "run", "dev"]