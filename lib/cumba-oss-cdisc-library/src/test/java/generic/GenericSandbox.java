package generic;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.cumba.cdisc.library.api.client.CdiscLibraryClient;
import net.cumba.cdisc.library.api.model.adam.AdamDataStructure;
import net.cumba.cdisc.library.api.model.adam.AdamProduct;
import net.cumba.cdisc.library.api.model.adam.AdamVariableSet;
import net.cumba.cdisc.library.api.model.ct.CtCodelist;
import net.cumba.cdisc.library.api.model.ct.CtPackage;
import net.cumba.cdisc.library.api.model.ct.CtPackageList;
import net.cumba.cdisc.library.api.model.ct.CtTerm;
import net.cumba.cdisc.library.api.model.products.Products;
import net.cumba.cdisc.library.api.model.rules.Rule;
import net.cumba.cdisc.library.api.model.rules.RuleMap;
import net.cumba.cdisc.library.api.model.rules.RulePackage;
import net.cumba.cdisc.library.api.model.sdtm.SdtmClass;
import net.cumba.cdisc.library.api.model.sdtm.SdtmDataset;
import net.cumba.cdisc.library.api.model.sdtm.SdtmVariable;
import net.cumba.datatable.help.CDT;
import net.cumba.web.api.Link;

public class GenericSandbox
{

    private static final Logger LOGGER = System.getLogger(GenericSandbox.class.getName());

    public static void main(String[] args)
    {
        String apiKey = System.getenv("CDISC_API_KEY");

        if (apiKey == null)
        {
            System.err.println("No api key set!");
            return;
        }

        CdiscLibraryClient client = CdiscLibraryClient.builder()//
                .apiKey(apiKey)//
                .cache(CdiscLibraryClient.getCache())//
                .build();

        try
        {
            // Sandbox toggles — uncomment one to exercise that branch of the API client. NOSONAR
            // S125
            // listProducts(client); NOSONAR S125
            // showAdamProducts(client); NOSONAR S125
            // showCodelists(client); NOSONAR S125
            // showSdtmDataSetsProducts(client); NOSONAR S125
            showSdtmClasses(client);
            // showRules(client); NOSONAR S125

        }
        catch (IOException ex)
        {
            LOGGER.log(Level.ERROR, ex.getMessage(), ex);

        }
    }


    public static void showRules(CdiscLibraryClient aClient) throws IOException
    {
        Products products = aClient.getProducts();

        Set<String> ruleTypes = new HashSet<String>();
        Set<String> sensitivity = new HashSet<String>();
        Set<String> executability = new HashSet<String>();

        for (Link pl : products.allLinks())
        {
            String vers = pl.id().orElse(null);
            String std = pl.id(-1).orElse(null);
            System.out.println(std + ":" + vers);
            RulePackage rp = aClient.getRules(std, vers);

            for (Rule rule : rp.rules().map(RuleMap::values).orElse(List.of()))
            {

                System.out.println("  -> " + rule.id().orElse("<no id>"));

                rule.ruleType().ifPresent(ruleTypes::add);
                rule.sensitivity().ifPresent(sensitivity::add);
                rule.executability().ifPresent(executability::add);

                rule.description().map(v -> "    -> Description=" + v)
                        .ifPresent(System.out::println);
                rule.ruleType().map(v -> "    -> ruleType=" + v).ifPresent(System.out::println);
                rule.sensitivity().map(v -> "    -> sensitivity=" + v)
                        .ifPresent(System.out::println);
                rule.executability().map(v -> "    -> executability=" + v)
                        .ifPresent(System.out::println);
                rule.scope().map(v -> "    -> scope=" + v).ifPresent(System.out::println);

            }

        }

        System.out.println("ruleTypes=" + String.join(", ", ruleTypes));
        System.out.println("sensitivity=" + String.join(", ", sensitivity));
        System.out.println("executability=" + String.join(", ", executability));

    }


    public static void showSdtmClasses(CdiscLibraryClient aClient) throws IOException
    {
        Products products = aClient.getProducts();

        for (Link pl : products.sdtmLinks())
        {
            String vers = pl.id().orElse(null);
            System.out.println(vers);

            // Endpoint: /mdr/sdtm/{version}/classes/ (uses the sdtm standard). NOSONAR S125
            List<Link> clsLinks = aClient.getSdtmClassLinks(vers);

            for (Link cl : clsLinks)
            {
                String clsId = cl.id().orElse("<no id>");

                System.out.println("  -> CLS:" + clsId);
                SdtmClass cls = aClient.getSdtmClass(vers, clsId);

                for (Link scl : cls.subclassLinks())
                {
                    System.out.println("    -> SC:" + scl);

                }
                for (SdtmDataset ds : cls.datasets())
                {
                    System.out.println("    -> DS:" + ds.name().orElse("<no name>"));

                    for (SdtmVariable variable : ds.datasetVariables())
                    {
                        System.out.println("      -> VAR:" + variable.name().orElse("<no name>"));
                    }

                }

                for (SdtmVariable variable : cls.classVariables())
                {
                    System.out.println("    -> VAR:" + variable.name().orElse("<no name>"));
                }

            }

        }
    }


    public static void listProducts(CdiscLibraryClient aClient) throws IOException
    {
        for (Link l : aClient.getProducts().allLinks())
        {
            System.out.println(l);
        }
    }


    public static void showSdtmDataSetsProducts(CdiscLibraryClient aClient) throws IOException
    {
        Map<String, CtCodelist> codelists = loadCodelists(aClient);

        Products products = aClient.getProducts();

        for (Link pl : products.sdtmigLinks())
        {
            String vers = pl.id().orElse(null);
            System.out.println(vers);

            // Alternative: use the "sdtm" standard to query against /mdr/sdtm/{version}/. NOSONAR
            // S125
            String standard = "sdtmig";

            List<Link> dsLinks = aClient.getSdtmDatasetLinks(standard, vers);

            for (Link l : dsLinks)
            {
                String dsName = l.id().orElse(null);
                if (dsName == null)
                {
                    continue;
                }

                SdtmDataset ds = aClient.getSdtmDataset(standard, vers, dsName);
                System.out.println(
                        "    -> " + ds.name().orElse("<no name>") + " " + ds.label().orElse(""));
                String[] varNames = ds.datasetVariables().stream().map(av ->
                {

                    String name = av.name().orElse(null);
                    if (name == null)
                    {
                        return "";
                    }

                    Optional<Link> cdlLnk = av.codelistLink();

                    if (cdlLnk.isPresent())
                    {
                        String cdlId = cdlLnk.flatMap(Link::id).orElse(null);
                        CtCodelist cdl = cdlId != null ? codelists.get(cdlId) : null;

                        return name + "["
                                + (cdl != null ? cdl.submissionValue().orElse("<ukn>") : "<ukn>")
                                + "]";
                    }
                    else if (!av.valueList().isEmpty())
                    {
                        return name + "[" + String.join(", ", av.valueList()) + "]";
                    }
                    else
                    {
                        return name + "[" + av.simpleDatatype().orElse("<ukn>") + "]";
                    }

                }).toArray(String[]::new);
                System.out.println("      -> " + String.join(", ", varNames));

            }
        }
    }


    public static void showAdamProducts(CdiscLibraryClient aClient) throws IOException
    {

        Map<String, CtCodelist> codelists = loadCodelists(aClient);

        Products products = aClient.getProducts();

        for (Link pl : products.adamLinks())
        {
            String id = pl.id().orElse(null);
            System.out.println(id);
            AdamProduct p = aClient.getAdamProduct(id);

            for (AdamDataStructure ads : p.dataStructures())
            {
                System.out.println("  -> " + ads.name().orElse(null));

                for (AdamVariableSet varSet : ads.analysisVariableSets())
                {
                    System.out.println("    -> " + varSet.name().orElse(null));

                    String[] varNames = varSet.analysisVariables().stream().map(av ->
                    {

                        String name = av.name().orElse(null);
                        if (name == null)
                        {
                            return "";
                        }

                        // Diagnostic helper for date-typed variables — keep as an example. NOSONAR
                        // S125
                        // if (name.endsWith("DT")) { System.out.println(av.label().get()); }
                        // NOSONAR S125
                        Optional<String> cdlId = av.codelistLink()//
                                .map(l -> l.id().orElse(null));

                        if (cdlId.isPresent())
                        {
                            CtCodelist cdl = codelists.get(cdlId.get());

                            return name + "[" + (cdl != null ? cdl.submissionValue().orElse("<ukn>")
                                    : "<ukn>") + "]";
                        }
                        else if (!av.valueList().isEmpty())
                        {
                            return name + "[" + String.join(", ", av.valueList()) + "]";
                        }
                        else
                        {
                            return name + "[" + av.simpleDatatype().orElse("<ukn>") + "]";
                        }

                    }).toArray(String[]::new);
                    System.out.println("      -> " + String.join(", ", varNames));
                }

            }
        }

    }


    public static Map<String, CtCodelist> loadCodelists(CdiscLibraryClient aClient)
        throws IOException
    {
        CtPackageList packages = aClient.getCtPackages();

        Map<String, CtCodelist> cdlMap = new HashMap<>();

        for (Link l : packages.packageLinks())
        {
            String id = l.id().orElse(null);
            if (id == null)
            {
                continue;
            }
            String base = CDT.getBeforeFirst(id, '-');
            if (!CDT.isIn(base, "adamct", "define", "sdtmct"))
            {
                continue;
            }
            CtPackage pkg = aClient.getCtPackage(id);
            for (CtCodelist cdl : pkg.codelists())
            {
                Optional<String> cid = cdl.conceptId();
                if (cid.isPresent())
                {
                    cdlMap.put(cid.get(), cdl);
                }
            }
        }
        return cdlMap;
    }


    public static void showCodelists(CdiscLibraryClient aClient) throws IOException
    {
        long start = System.currentTimeMillis();
        CtPackageList packages = aClient.getCtPackages();

        Map<String, CtCodelist> cdlMap = new HashMap<>();

        for (Link l : packages.packageLinks())
        {
            System.out.println(l);
            String id = l.id().orElse(null);
            if (id == null)
            {
                continue;
            }
            String base = CDT.getBeforeFirst(id, '-');
            if (!CDT.isIn(base, "adamct", "define", "sdtmct"))
            {
                continue;
            }
            CtPackage pkg = aClient.getCtPackage(id);
            for (CtCodelist cdl : pkg.codelists())
            {
                if (cdl.submissionValue().isPresent())
                {
                    cdlMap.put(cdl.name().orElse("<unnamed>"), cdl);
                }
            }
        }

        List<CtCodelist> codelists = new ArrayList<CtCodelist>(cdlMap.values());

        Collections.sort(codelists, (c1, c2) -> c1.submissionValue().orElse("")
                .compareTo(c2.submissionValue().orElse("")));

        System.out.println("Collected %d codelists.".formatted(codelists.size()));

        long count = 0;

        for (CtCodelist cdl : codelists)
        {
            if (cdl.conceptId().equals(Optional.of("C66769")))
            {
                System.out.println(cdl);
            }
            System.out.println("  ->" + cdl.submissionValue());

            List<CtTerm> terms = cdl.terms();
            count += terms.size();
            String[] values = terms.stream().map(CtTerm::submissionValue)
                    .filter(Optional::isPresent).map(Optional::get).toArray(String[]::new);

            System.out.println(
                    "    -> (%d values): %s".formatted(values.length, String.join(", ", values)));
        }

        long rt = System.currentTimeMillis() - start;
        System.out.println("All %d codelists with %d terms loaded in %d ms."
                .formatted(codelists.size(), count, rt));
    }
}
