package com.epam.indigoeln.core.service.calculation;

import com.chemistry.enotebook.StoichCalculator;
import com.chemistry.enotebook.domain.*;
import com.chemistry.enotebook.experiment.datamodel.batch.BatchType;
import com.chemistry.enotebook.experiment.datamodel.batch.BatchTypeFactory;
import com.chemistry.enotebook.experiment.datamodel.batch.InvalidBatchTypeException;
import com.epam.indigo.Indigo;
import com.epam.indigo.IndigoObject;
import com.epam.indigo.IndigoRenderer;
import com.epam.indigoeln.core.service.calculation.helper.RendererResult;
import com.epam.indigoeln.core.service.codetable.CodeTableService;
import com.epam.indigoeln.web.rest.dto.calculation.ReactionPropertiesDTO;
import com.epam.indigoeln.web.rest.dto.calculation.StoicBatchDTO;
import com.epam.indigoeln.web.rest.dto.calculation.StoicTableDTO;
import com.epam.indigoeln.web.rest.dto.common.StringValueDTO;
import com.epam.indigoeln.web.rest.dto.common.UnitValueDTO;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.chemistry.enotebook.experiment.common.units.UnitType.*;

/**
 * Service for calculations under reaction or molecular structures defined in special text format
 */
@Service
public class CalculationService {

    private static final String SALT_CODE    = "SALT_CODE";
    private static final String SALT_DESC    = "SALT_DESC";
    private static final String SALT_WEIGHT  = "SALT_WEIGHT";
    private static final String SALT_FORMULA = "SALT_FORMULA";

    private static final Map<String, String> SALT_METADATA_DEFAULT = ImmutableMap.of(
            SALT_CODE, "0",
            SALT_DESC, "Parent Structure",
            SALT_WEIGHT, "0",
            SALT_FORMULA, "");

    @Autowired
    private Indigo indigo;

    @Autowired
    private IndigoRenderer indigoRenderer;

    @Autowired
    private CodeTableService codeTableService;

    /**
     * Check, that chemistry structures of reactions or molecules are equals
     *
     * @param chemistryItems list of chemistry structures
     * @param isReaction are chemistry items reactions or molecules
     * @return true if all chemistry items equals
     */
    public boolean chemistryEquals(List<String> chemistryItems, boolean isReaction) {
        IndigoObject prevHandle = null;
        for(String chemistry : chemistryItems) {
            IndigoObject handle = isReaction ? indigo.loadReaction(chemistry) : indigo.loadMolecule(chemistry);
            if(prevHandle != null && indigo.exactMatch(handle, prevHandle) == null) {
                return false;
            }
            prevHandle = handle;
        }
        return true;
    }

    /**
     * Return calculated information for molecular structure
     * (name, formula, molecular weight, exact molecular weight, is chiral)
     *
     * @param molecule structure of molecule
     * @return map of calculated attributes
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getMolecularInformation(String molecule, Optional<String> saltCodeOpt, Optional<Float> saltEqOpt) {
        Map<String, String> result = new HashMap<>();

        IndigoObject handle = indigo.loadMolecule(molecule);

        Map<String, String> saltMetadata = getSaltMetadata(saltCodeOpt).orElse(SALT_METADATA_DEFAULT);
        float saltEq = saltEqOpt.orElse(1.0f);
        float molecularWeightOriginal = handle.molecularWeight();
        float saltWeight =  Optional.ofNullable(saltMetadata.get(SALT_WEIGHT)).map(Float::valueOf).orElse(0.0f);
        float molecularWeightCalculated = molecularWeightOriginal + saltEq * saltWeight;

        result.put("name", handle.name());
        result.put("molecule", molecule);
        result.put("molecularFormula", handle.grossFormula());
        result.put("molecularWeightOriginal", String.valueOf(molecularWeightOriginal));
        result.put("exactMolecularWeight", String.valueOf(handle.monoisotopicMass()));
        result.put("isChiral", String.valueOf(handle.isChiral()));
        result.put("molecularWeight", String.valueOf(molecularWeightCalculated));
        result.put("saltCode", saltMetadata.get(SALT_CODE));
        result.put("saltDesc", saltMetadata.get(SALT_DESC));
        result.put("saltFormula", saltMetadata.get(SALT_FORMULA));
        result.put("saltWeight", String.valueOf(saltWeight));
        result.put("saltEQ", String.valueOf(saltEq));

        return result;
    }

    /**
     * Check, that molecule is empty (does not contains any atoms)
     *
     * @param molecule structure of molecule
     * @return true if molecule empty
     */
    public boolean isMoleculeEmpty(String molecule) {
        return indigo.loadMolecule(molecule).countAtoms() == 0;
    }

    /**
     * Check, that molecule is chiral
     *
     * @param molecule structure of molecule
     * @return true if molecule is chiral
     */
    public boolean isMoleculeChiral(String molecule) {
        return indigo.loadMolecule(molecule).isChiral();
    }

    /**
     * Extract components (products and reactants) of given reaction
     * @param reaction reaction structure
     * @return reaction components
     */
    public ReactionPropertiesDTO extractReactionComponents(String reaction) {
        IndigoObject handle = indigo.loadReaction(reaction);

        //fetch reactants
        List<String> reactants = new ArrayList<>();
        for(IndigoObject reactant : handle.iterateReactants()) {
            reactants.add(reactant.molfile());
        }

        //fetch components
        List<String> products = new ArrayList<>();
        for(IndigoObject product : handle.iterateProducts()) {
            products.add(product.molfile());
        }

        return new ReactionPropertiesDTO(reaction, reactants, products);
    }

    /**
     * Combine reaction components (products and reactants) with existing reaction structure
     * Reaction structure received as string field of DTO and will be enriched by reactants and products received in
     * DTO list fields
     * @param reactionDTO reaction DTO
     * @return reaction DTO enriched by reactants and products
     */
    public ReactionPropertiesDTO combineReactionComponents(ReactionPropertiesDTO reactionDTO) {
        IndigoObject handle = indigo.createReaction();

        //add reactants to the structure
        for (String reactant : reactionDTO.getReactants()) {
            handle.addReactant(indigo.loadMolecule(reactant));
        }

        //add products to the structure
        for (String product : reactionDTO.getReactants()) {
            handle.addProduct(indigo.loadMolecule(product));
        }

        reactionDTO.setStructure(handle.rxnfile());
        return reactionDTO;
    }

    /**
     * Method to determine if chemistry contains a valid reaction (any products and reactants present)
     *
     * @param reaction structure of reaction
     * @return is reaction valid
     */
    public boolean isValidReaction(String reaction) {
        IndigoObject handle = indigo.loadQueryReaction(reaction);
        return (handle.countReactants() > 0) && (handle.countProducts() > 0);
    }

    /**
     * Render molecule/reaction by its string representation
     * @param structure string structure representation (Mol, Smiles etc.)
     * @param structureType molecule or reaction
     * @return RendererResult
     */
    public RendererResult getStructureWithImage(String structure, String structureType) {
        IndigoObject io = StringUtils.equals(structureType, "molecule") ? indigo.loadMolecule(structure) :
                indigo.loadReaction(structure);

        // auto-generate coordinates as Bingo DB doesn't store them
        io.layout();

        return new RendererResult(indigoRenderer.renderToBuffer(io));
    }

    private Optional<Map> getSaltMetadata(Optional<String> saltCode) {
        if(!saltCode.isPresent()) {
            return Optional.empty();
        }

        List<Map> codeTable = codeTableService.getCodeTable(CodeTableService.TABLE_SALT_CODE);
        return codeTable.stream().filter(tableRow -> saltCode.get().equals(tableRow.get(SALT_CODE))).findAny();
    }

    /**
     * Recalculates stoic table batches, actual product and intended product batches
     *
     * @param stoicTableDTO contains stoic table batches, actual product batches (product batch summary)
     *                      and intended product batches
     * @return stoicTableDTO with recalculated batches
     */
    public StoicTableDTO calculateStoicTable(StoicTableDTO stoicTableDTO) {
        MonomerBatchModel batchModel = (MonomerBatchModel) createBatchModelForCalculation(stoicTableDTO.getChangedBatch(), new MonomerBatchModel());
        ReactionStepModel reactionStepModel = createReactionStepModelForCalculation(stoicTableDTO);

        StoichCalculator stoichCalculator = new StoichCalculator(reactionStepModel);
        stoichCalculator.recalculateStoichBasedOnBatch(batchModel, false);

        return prepareStoicTableForResponse(reactionStepModel);
    }

    private ReactionStepModel createReactionStepModelForCalculation(StoicTableDTO data) {
        List<StoicBatchDTO> stoicBatches = data.getStoicBatches();
        List<StoicBatchDTO> intendedProducts = data.getIntendedProducts();
        List<StoicBatchDTO> actualProducts = data.getActualProducts();

        BatchesList<MonomerBatchModel> stoicBatchesList = prepareStoicBatchesList(stoicBatches);
        ArrayList<BatchesList<ProductBatchModel>> productBatchesList = prepareProductBatchesList(intendedProducts, actualProducts);

        ReactionStepModel reactionStepModel = new ReactionStepModel();
        reactionStepModel.setStoicBatchesList(stoicBatchesList);
        reactionStepModel.setProducts(productBatchesList);
        return reactionStepModel;
    }

    private BatchesList<MonomerBatchModel> prepareStoicBatchesList(List<StoicBatchDTO> stoicBatches) {
        BatchesList<MonomerBatchModel> stoicBatchesList = new BatchesList<>();
        for (StoicBatchDTO sourceMonomerBatch : stoicBatches) {
            MonomerBatchModel monomer = (MonomerBatchModel) createBatchModelForCalculation(sourceMonomerBatch, new MonomerBatchModel());
            stoicBatchesList.addBatch(monomer);
        }
        return stoicBatchesList;
    }

    private ArrayList<BatchesList<ProductBatchModel>> prepareProductBatchesList(List<StoicBatchDTO> intendedProducts, List<StoicBatchDTO> actualProducts) {
        ArrayList<BatchesList<ProductBatchModel>> productBatchesList = new ArrayList<>();
        // intended products
        for (StoicBatchDTO sourceProductBatch : intendedProducts) {
            ProductBatchModel productBatchModel = (ProductBatchModel) createBatchModelForCalculation(sourceProductBatch, new ProductBatchModel());
            BatchesList<ProductBatchModel> productBatches = new BatchesList<>();
            productBatchModel.setBatchType(BatchType.INTENDED_PRODUCT);
            productBatches.addBatch(productBatchModel);
            productBatchesList.add(productBatches);
        }

        // actual products
        for (StoicBatchDTO sourceProductBatch : actualProducts) {
            ProductBatchModel productBatchModel = (ProductBatchModel) createBatchModelForCalculation(sourceProductBatch, new ProductBatchModel());
            BatchesList<ProductBatchModel> productBatches = new BatchesList<>();
            productBatchModel.setBatchType(BatchType.ACTUAL_PRODUCT);
            productBatches.addBatch(productBatchModel);
            productBatchesList.add(productBatches);
        }
        return productBatchesList;
    }

    private StoicTableDTO prepareStoicTableForResponse(ReactionStepModel reactionStepModel) {
        List<StoicBatchDTO> stoicBatches = convertMonomerBatchesListForResponse(reactionStepModel.getBatchesFromStoicBatchesList());
        List<StoicBatchDTO> intendedProducts = convertProductBatchesListForResponse(reactionStepModel.getIntendedProductBatches());
        List<StoicBatchDTO> actualProducts = convertProductBatchesListForResponse(reactionStepModel.getActualProductBatches());
        return new StoicTableDTO(stoicBatches, intendedProducts, actualProducts);
    }

    private List<StoicBatchDTO> convertProductBatchesListForResponse(List<ProductBatchModel> sourceBatches) {
        List<StoicBatchDTO> myBatches = new ArrayList<>();
        for (ProductBatchModel sourceBatch : sourceBatches) {
            myBatches.add(convertBatchModelForResponse(sourceBatch, new StoicBatchDTO()));
        }
        return myBatches;
    }

    private List<StoicBatchDTO> convertMonomerBatchesListForResponse(List<MonomerBatchModel> sourceBatches) {
        List<StoicBatchDTO> myBatches = new ArrayList<>();
        for (MonomerBatchModel sourceBatch : sourceBatches) {
            myBatches.add(convertBatchModelForResponse(sourceBatch, new StoicBatchDTO()));
        }
        return myBatches;
    }

    private BatchModel createBatchModelForCalculation(StoicBatchDTO sourceBatch, BatchModel targetBatch) {
        targetBatch.setLimiting(sourceBatch.isLimiting());
        try {
            targetBatch.setBatchType(BatchTypeFactory.getBatchType(sourceBatch.getRxnRole().getName()));
        } catch (InvalidBatchTypeException e) {
            e.printStackTrace();
        }
        targetBatch.setDensityAmount(new AmountModel(DENSITY, sourceBatch.getDensity().getValue()));
        targetBatch.setMolarAmount(new AmountModel(MOLAR, sourceBatch.getMolarity().getValue()));
        targetBatch.setMoleAmount(new AmountModel(MOLES, sourceBatch.getMol().getValue())); // todo check, should be mass to volume?
        targetBatch.setMolecularWeightAmount(new AmountModel(MASS, sourceBatch.getMolWeight())); //todo check unit type for mW (g/mol)
        targetBatch.setRxnEquivsAmount(new AmountModel(SCALAR, sourceBatch.getEq()));
        targetBatch.setVolumeAmount(new AmountModel(VOLUME, sourceBatch.getVolume().getValue()));
        targetBatch.setWeightAmount(new AmountModel(MASS, sourceBatch.getWeight().getValue()));
        targetBatch.setPurity(sourceBatch.getStoicPurity()); // todo check purityAmount or stoicPurityAmount
//        targetBatch.setTheoreticalMoleAmount(new AmountModel(UnitType.MOLES, sourceBatch.getTheoMoles().getValue()));
//        targetBatch.setTheoreticalWeightAmount(new AmountModel(MASS, sourceBatch.getTheoWeight().getValue()));
        // + yield
        return targetBatch;
    }

    private StoicBatchDTO convertBatchModelForResponse(BatchModel sourceBatch, StoicBatchDTO targetBatch) {
        targetBatch.setLimiting(sourceBatch.isLimiting());
        targetBatch.setRxnRole(new StringValueDTO(sourceBatch.getBatchType().toString()));
        targetBatch.setDensity(new UnitValueDTO(sourceBatch.getDensityAmount().doubleValue(), sourceBatch.getDensityAmount().getUnit().getDisplayValue()));
        targetBatch.setMolarity(new UnitValueDTO(sourceBatch.getMolarAmount().doubleValue(), sourceBatch.getMolarAmount().getUnit().getDisplayValue()));
        targetBatch.setMol(new UnitValueDTO(sourceBatch.getMoleAmount().doubleValue(), sourceBatch.getMoleAmount().getUnit().getDisplayValue())); // todo check, should be mass to volume?
        targetBatch.setMolWeight(sourceBatch.getMolecularWeightAmount().doubleValue()); //todo check unit type for mW (g/mol)
        targetBatch.setEq(sourceBatch.getRxnEquivsAmount().doubleValue()); // todo check rxnEquivsAmount or stoicRxnEquivsAmount
        targetBatch.setVolume(new UnitValueDTO(sourceBatch.getVolumeAmount().doubleValue(), sourceBatch.getVolumeAmount().getUnit().getDisplayValue()));
        targetBatch.setWeight(new UnitValueDTO(sourceBatch.getWeightAmount().doubleValue(), sourceBatch.getWeightAmount().getUnit().getDisplayValue()));
        targetBatch.setStoicPurity(sourceBatch.getPurityAmount().doubleValue());
//        targetBatch.setTheoMoles(new UnitValueDTO(sourceBatch.getTheoreticalMoleAmount().doubleValue(), sourceBatch.getTheoreticalMoleAmount().getUnit().getDisplayValue()));
//        targetBatch.setTheoWeight(new UnitValueDTO(sourceBatch.getTheoreticalWeightAmount().doubleValue(), sourceBatch.getTheoreticalWeightAmount().getUnit().getDisplayValue()));
        // + yield
        return targetBatch;
    }
}
