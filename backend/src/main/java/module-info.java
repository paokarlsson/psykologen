/** Psykologens backend på Spola: allt kopplas ihop i {@code PsykologenApplication.main}. */
module psykologen {
    requires spola.http;
    requires spola.json;
    requires spola.web;
    requires spola.config;
    requires spola.util;
    requires spola.security;
    requires spola.security.web;
    requires spola.client;
}
