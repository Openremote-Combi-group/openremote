FROM node:20-alpine

WORKDIR /app

# Install only what's needed
COPY package.json ./
RUN npm ci --omit=dev || npm install --omit=dev

# Copy source
COPY index.mjs ./

ENV NODE_ENV=production

# Default envs can be overridden by compose
ENV PGHOST=postgresql \
    PGPORT=5432 \
    PGUSER=postgres \
    PGPASSWORD=postgres \
    PGDATABASE=openremote \
    PGSSL=false

CMD ["node", "index.mjs"]


