FROM nginx:1.19.0

# Update packages
RUN apt update -y --fix-missing

# Install common packages
RUN apt install -y ca-certificates ssh software-properties-common gnupg wget openssl

# Install utils
RUN apt install -y nano ssh inetutils-ping net-tools htop telnet curl redis-tools ncdu rsync git whois dnsutils

# Copy configs and remove defaults
COPY nginx/production/ /etc/nginx/
RUN rm -rf /etc/nginx/conf.d/default.conf

# Run nginx without self-daemonize
CMD ["nginx", "-g", "daemon off;"]
