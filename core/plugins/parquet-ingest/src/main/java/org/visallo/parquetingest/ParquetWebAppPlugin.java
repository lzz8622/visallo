package org.visallo.parquetingest;

import com.v5analytics.webster.Handler;
import org.apache.commons.io.IOUtils;
import org.semanticweb.owlapi.model.IRI;
import org.vertexium.Authorizations;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.ingestontologymapping.StructuredFileOntology;
import org.visallo.parquetingest.routes.ParquetFileAnalyze;
import org.visallo.web.AuthenticationHandler;
import org.visallo.web.VisalloCsrfHandler;
import org.visallo.web.WebApp;
import org.visallo.web.WebAppPlugin;
import org.visallo.web.privilegeFilters.ReadPrivilegeFilter;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import java.io.InputStream;

@Name("Parquet Ingest")
@Description("Supports importing structured data from CSV and Excel")
public class ParquetWebAppPlugin implements WebAppPlugin {
        private final OntologyRepository ontologyRepository;
        private final UserRepository userRepository;
        private final AuthorizationRepository authorizationRepository;

        @Inject
        public ParquetWebAppPlugin(
                OntologyRepository ontologyRepository,
                UserRepository userRepository,
                AuthorizationRepository authorizationRepository
        ) {
            this.ontologyRepository = ontologyRepository;
            this.userRepository = userRepository;
            this.authorizationRepository = authorizationRepository;
        }

        @Override
        public void init(WebApp app, ServletContext servletContext, Handler authenticationHandler) {
            Class<? extends Handler> authenticator = AuthenticationHandler.class;
            Class<? extends Handler> csrfProtector = VisalloCsrfHandler.class;

            ensureOntologyDefined();

            app.post(
                    "/s3-parquet/analyze",
                    authenticator,
                    csrfProtector,
                    ReadPrivilegeFilter.class,
                    ParquetFileAnalyze.class
            );
        }

        private void ensureOntologyDefined() {
            if (ontologyRepository.isOntologyDefined(StructuredFileOntology.IRI)) {
                return;
            }

            try (InputStream structuredFileOwl = StructuredFileOntology.class.getResourceAsStream("structured-file.owl")) {
                byte[] inFileData = IOUtils.toByteArray(structuredFileOwl);
                IRI tagIRI = IRI.create(StructuredFileOntology.IRI);
                Authorizations authorizations = authorizationRepository.getGraphAuthorizations(this.userRepository.getSystemUser());
                ontologyRepository.importFileData(inFileData, tagIRI, null, authorizations);
            } catch (Exception e) {
                throw new VisalloException("Could not read structured-file.owl file", e);
            }
        }

}
