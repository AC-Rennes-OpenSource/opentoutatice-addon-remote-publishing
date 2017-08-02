/**
 * 
 */
package fr.toutatice.ecm.acrennes.ui;

import java.io.Serializable;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;

import fr.toutatice.ecm.acrennes.builder.UserRootSectionsBuilder;
import fr.toutatice.ecm.acrennes.model.UserRootSections;
import fr.toutatice.ecm.platform.constants.ExtendedSeamPrecedence;


/**
 * @author david
 *
 */
@Name("sectionsManager")
@Scope(ScopeType.EVENT)
@Install(precedence = ExtendedSeamPrecedence.ADD_ON)
public class AcrennesUserRootSectionsManager implements Serializable {

    private static final long serialVersionUID = 4068413633653116010L;

    @In(create = true)
    protected transient NavigationContext navigationContext;

    @In(create = true)
    protected transient CoreSession documentManager;

    private transient UserRootSections sections;


    public UserRootSections getSections() {
        if (this.sections == null) {
            DocumentModel currentDocument = this.navigationContext.getCurrentDocument();
            this.sections = new UserRootSectionsBuilder(this.documentManager).buildSectionsModel(currentDocument);
        }
        return this.sections;
    }

    /**
     * @param sections the sections to set
     */
    public void setSections(UserRootSections sections) {
        this.sections = sections;
    }


}
