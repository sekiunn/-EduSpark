# syntax=docker/dockerfile:1

FROM postgres:16-bookworm

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates lsb-release wget postgresql-common \
    && /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y \
    && wget -O /tmp/groonga-apt-source.deb "https://packages.groonga.org/debian/groonga-apt-source-latest-$(lsb_release --codename --short).deb" \
    && apt-get install -y --no-install-recommends /tmp/groonga-apt-source.deb \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
      postgresql-16-pgvector \
      postgresql-16-pgdg-pgroonga \
    && rm -rf /var/lib/apt/lists/* /tmp/groonga-apt-source.deb
