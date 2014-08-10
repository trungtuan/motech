package org.motechproject.mds.web.controller;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.motechproject.mds.dto.FieldInstanceDto;
import org.motechproject.mds.dto.TypeDto;
import org.motechproject.mds.ex.EntityNotFoundException;
import org.motechproject.mds.filter.Filter;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.service.EntityService;
import org.motechproject.mds.util.Order;
import org.motechproject.mds.web.domain.EntityRecord;
import org.motechproject.mds.web.domain.FieldRecord;
import org.motechproject.mds.web.domain.GridSettings;
import org.motechproject.mds.web.domain.HistoryRecord;
import org.motechproject.mds.web.domain.Records;
import org.motechproject.mds.web.service.InstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang.CharEncoding.UTF_8;
import static org.motechproject.mds.util.Constants.Roles;

/**
 * The <code>InstanceController</code> is the Spring Framework Controller used by view layer for
 * managing entity instances.
 *
 * @see org.motechproject.mds.dto.FieldDto
 * @see org.motechproject.mds.dto.EntityDto
 */
@Controller
public class InstanceController extends MdsController {

    @Autowired
    private EntityService entityService;
    @Autowired
    private InstanceService instanceService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @RequestMapping(value = "/instances", method = RequestMethod.POST)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseStatus(HttpStatus.OK)
    public void saveInstance(@RequestBody EntityRecord record) {
        instanceService.saveInstance(decodeBlobFiles(record));
    }

    @RequestMapping(value = "/instances/{instanceId}", method = RequestMethod.POST)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseStatus(HttpStatus.OK)
    public void updateInstance(@RequestBody EntityRecord record) {
        instanceService.saveInstance(decodeBlobFiles(record));
    }

    @RequestMapping(value = "/instances/deleteBlob/{entityId}/{instanceId}/{fieldId}", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseStatus(HttpStatus.OK)
    public void deleteBlobContent(@PathVariable Long entityId, @PathVariable Long instanceId, @PathVariable Long fieldId) {
        EntityRecord record = instanceService.getEntityInstance(entityId, instanceId);
        instanceService.saveInstance(record, fieldId);
    }

    @RequestMapping(value = "/instances/{entityId}/new")
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public EntityRecord newInstance(@PathVariable Long entityId) {
        return instanceService.newInstance(entityId);
    }

    @RequestMapping(value = "/instances/{entityId}/{instanceId}/fields", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public List<FieldInstanceDto> getInstanceFields(@PathVariable Long entityId, @PathVariable Long instanceId) {
        return instanceService.getInstanceFields(entityId, instanceId);
    }

    @RequestMapping(value = "/instances/{entityId}/{instanceId}/{fieldName}", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public void getBlobField(@PathVariable Long entityId, @PathVariable Long instanceId, @PathVariable String fieldName, HttpServletResponse response) throws IOException {
        byte[] content = ArrayUtils.toPrimitive((Byte[]) instanceService.getInstanceField(entityId, instanceId, fieldName));

        try (OutputStream outputStream = response.getOutputStream()) {
            response.setHeader("Accept-Ranges", "bytes");

            if (content.length == 0) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
            }

            outputStream.write(content);
        }
    }

    @RequestMapping(value = "/instances/{entityId}/delete/{instanceId}", method = RequestMethod.DELETE)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseStatus(HttpStatus.OK)
    public void deleteInstance(@PathVariable Long entityId, @PathVariable Long instanceId) {
        instanceService.deleteInstance(entityId, instanceId);
    }

    @RequestMapping(value = "/instances/{entityId}/revertFromTrash/{instanceId}", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseStatus(HttpStatus.OK)
    public void revertInstanceFromTrash(@PathVariable Long entityId, @PathVariable Long instanceId) {
        instanceService.revertInstanceFromTrash(entityId, instanceId);
    }

    @RequestMapping(value = "/instances/{entityId}/{instanceId}/history", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public Records<HistoryRecord> getHistory(@PathVariable Long entityId, @PathVariable Long instanceId, GridSettings settings) {
        Order order = null;
        if (settings.getSortColumn() != null && !settings.getSortColumn().isEmpty()) {
            order = new Order(settings.getSortColumn(), settings.getSortDirection());
        }

        if (settings.getPage() == null) {
            settings.setPage(1);
            settings.setRows(10);
        }
        QueryParams queryParams = new QueryParams(settings.getPage(), settings.getRows(), order);
        List<HistoryRecord> historyRecordsList = instanceService.getInstanceHistory(entityId, instanceId, queryParams);

        long recordCount = instanceService.countHistoryRecords(entityId, instanceId);
        int rowCount = (int) Math.ceil(recordCount / (double) settings.getRows());

        return new Records<>(settings.getPage(), rowCount, (int) recordCount, historyRecordsList);
    }

    @RequestMapping(value = "/instances/{entityId}/{instanceId}/previousVersion/{historyId}", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public HistoryRecord getPreviousInstance(@PathVariable Long entityId, @PathVariable Long instanceId, @PathVariable Long historyId) {
        HistoryRecord historyRecord = instanceService.getHistoryRecord(entityId, instanceId, historyId);
        if (historyRecord == null) {
            throw new EntityNotFoundException();
        }
        return historyRecord;
    }

    @RequestMapping(value = "/instances/{entityId}/{instanceId}/revert/{historyId}", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public void revertPreviousVersion(@PathVariable Long entityId, @PathVariable Long instanceId, @PathVariable Long historyId) {
        instanceService.revertPreviousVersion(entityId, instanceId, historyId);
    }

    @RequestMapping(value = "/instances/{entityId}/instance/{instanceId}", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public EntityRecord getInstance(@PathVariable Long entityId, @PathVariable Long instanceId) {
        return instanceService.getEntityInstance(entityId, instanceId);
    }

    @RequestMapping(value = "/entities/{entityId}/trash", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public Records<EntityRecord> getTrash(@PathVariable Long entityId, GridSettings settings) {
        Order order = null;
        if (settings.getSortColumn() != null && !settings.getSortColumn().isEmpty()) {
            order = new Order(settings.getSortColumn(), settings.getSortDirection());
        }

        QueryParams queryParams = new QueryParams(settings.getPage(), settings.getRows(), order);
        List<EntityRecord> trashRecordsList = instanceService.getTrashRecords(entityId, queryParams);

        long recordCount = instanceService.countTrashRecords(entityId);
        int rowCount = (int) Math.ceil(recordCount / (double) settings.getRows());

        return new Records<>(settings.getPage(), rowCount, (int) recordCount, trashRecordsList);

    }

    @RequestMapping(value = "/entities/{entityId}/trash/{instanceId}", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public EntityRecord getSingleTrashInstance(@PathVariable Long entityId, @PathVariable Long instanceId) {
        return instanceService.getSingleTrashRecord(entityId, instanceId);
    }

    @RequestMapping(value = "/entities/{entityId}/exportInstances", method = RequestMethod.GET)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    public void exportEntityInstances(@PathVariable Long entityId, HttpServletResponse response) throws IOException {
        if (null == entityService.getEntity(entityId)) {
            throw new EntityNotFoundException();
        }

        List<EntityRecord> entityRecords = instanceService.getEntityRecords(entityId);

        try (CsvMapWriter csvMapWriter = new CsvMapWriter(response.getWriter(), CsvPreference.STANDARD_PREFERENCE)) {

            List<Map<String, String>> csvMap = prepareForCsvConversion(entityRecords);
            Set<String> headerValues = csvMap.get(0).keySet();
            String[] headers = headerValues.toArray(new String[headerValues.size()]);

            String fileName = "Entity_" + entityId + "_instances";
            response.setContentType("text/csv");
            response.setCharacterEncoding(UTF_8);
            response.setHeader(
                    "Content-Disposition",
                    "attachment; filename=" + fileName + ".csv");

            csvMapWriter.writeHeader(headers);

            for (Map<String, String> row : csvMap) {
                csvMapWriter.write(row, headers);
            }
        }
    }

    @RequestMapping(value = "/entities/{entityId}/instances", method = RequestMethod.POST)
    @PreAuthorize(Roles.HAS_DATA_ACCESS)
    @ResponseBody
    public Records<?> getInstances(@PathVariable Long entityId, GridSettings settings) throws IOException {
        Order order = null;
        if (!settings.getSortColumn().isEmpty()) {
            order = new Order(settings.getSortColumn(), settings.getSortDirection());
        }

        QueryParams queryParams = new QueryParams(settings.getPage(), settings.getRows(), order);

        String lookup = settings.getLookup();
        String filterStr = settings.getFilter();

        List<EntityRecord> entityRecords;
        long recordCount;

        if (StringUtils.isNotBlank(lookup)) {
            entityRecords = instanceService.getEntityRecordsFromLookup(entityId, lookup, getFields(settings), queryParams);
            recordCount = instanceService.countRecordsByLookup(entityId, lookup, getFields(settings));
        } else if (filterSet(filterStr)) {
            Filter filter = objectMapper.readValue(filterStr, Filter.class);
            entityRecords = instanceService.getEntityRecordsWithFilter(entityId, filter, queryParams);
            recordCount = instanceService.countRecordsWithFilter(entityId, filter);
        } else {
            entityRecords = instanceService.getEntityRecords(entityId, queryParams);
            recordCount = instanceService.countRecords(entityId);
        }

        int rowCount = (int) Math.ceil(recordCount / (double) settings.getRows());

        return new Records<>(settings.getPage(), rowCount, (int) recordCount, entityRecords);
    }

    private Map<String, Object> getFields(GridSettings gridSettings) throws IOException {
        return objectMapper.readValue(gridSettings.getFields(), new TypeReference<HashMap>() {
        });
    }

    private boolean filterSet(String filterStr) {
        return StringUtils.isNotBlank(filterStr) && !"{}".equals(filterStr);
    }

    private EntityRecord decodeBlobFiles(EntityRecord record) {
        for (FieldRecord field : record.getFields()) {
            if (TypeDto.BLOB.getTypeClass().equals(field.getType().getTypeClass())) {
                byte[] content = field.getValue() != null ?
                        field.getValue().toString().getBytes() :
                        ArrayUtils.EMPTY_BYTE_ARRAY;

                field.setValue(decodeBase64(content));
            }
        }
        return record;
    }

    private List<Map<String, String>> prepareForCsvConversion(List<EntityRecord> entityList) {
        List<Map<String, String>> list = new ArrayList<>();

        for (EntityRecord entityRecord : entityList) {
            Map<String, String> fieldValues = new LinkedHashMap<>();
            for (FieldRecord fieldRecord : entityRecord.getFields()) {
                Object value = fieldRecord.getValue();
                fieldValues.put(fieldRecord.getDisplayName(), value == null ? "" : value.toString());
            }
            list.add(fieldValues);
        }

        return list;
    }

    private Byte[] decodeBase64(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }

        Base64 decoder = new Base64();
        //We must remove "data:(content type);base64," prefix and then decode content
        int index = ArrayUtils.indexOf(content, (byte) ',') + 1;

        return ArrayUtils.toObject(decoder.decode(ArrayUtils.subarray(content, index, content.length)));
    }

    @Autowired
    public void setEntityService(EntityService entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setInstanceService(InstanceService instanceService) {
        this.instanceService = instanceService;
    }
}
