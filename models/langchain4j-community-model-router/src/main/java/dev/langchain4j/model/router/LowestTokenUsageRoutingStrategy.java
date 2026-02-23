package dev.langchain4j.model.router;

import java.util.List;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.output.TokenUsage;

/**
 * A {@link ModelRoutingStrategy} that picks the model with the lowest recorded
 * total token usage. Token usage is stored under the
 * {@link LowestTokenUsageRoutingStrategy#TOTAL_TOKEN_USAGE} key in each
 * {@link ChatModelWrapper}'s routing metadata.
 */
@Experimental
public class LowestTokenUsageRoutingStrategy implements ModelRoutingStrategy {

    /**
     * Metadata key storing the accumulated total token usage for a wrapped model.
     */
    public static final String                  TOTAL_TOKEN_USAGE = "TOTAL_TOKEN_USAGE";
    
    @Override
    public ChatModelWrapper route(List<ChatModelWrapper> availableModels, ChatRequest chatRequest) {
    	// add FailureListener to each not already having one
    	availableModels.stream()
        .filter(m -> m.listeners().stream().noneMatch(UsageTrackerListener.class::isInstance))
        .forEach(m -> m.addListener(new UsageTrackerListener(m)));
    	
    	ChatModelWrapper selectedModel = null;
        long selectedUsage = Long.MAX_VALUE;
        
        for (ChatModelWrapper entry : availableModels) {
            long routeUsage = readUsage(entry);
            if (routeUsage < selectedUsage) {
            	selectedModel = entry;
                selectedUsage = routeUsage;
            }
        }
        
        return selectedModel;
    }
    
    private long readUsage(ChatModelWrapper wrapper) {
        Object metadata = wrapper.getMetadata(TOTAL_TOKEN_USAGE);
        if (metadata instanceof Number) {
            return ((Number) metadata).longValue();
        }
        return 0L;
    }
    
    /**
     * A ChatModelListener which set error metadata on the wrapper
     */
    class UsageTrackerListener implements ChatModelListener {
    	
    	private ChatModelWrapper wrapper; 
    	// we need to remember the wrapper as the listener does not have access to the model
    	public UsageTrackerListener(ChatModelWrapper wrapper) {
    		this.wrapper = wrapper;
    	}
    	
    	@Override
    	public void onResponse(ChatModelResponseContext responseContext) {
            TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();
            Integer totalTokenCount = tokenUsage == null ? null : tokenUsage.totalTokenCount();
            
            if (totalTokenCount == null) {
                return;
            }
            
            Object existingMetadata = wrapper.getMetadata(TOTAL_TOKEN_USAGE);
            int previousTotal = existingMetadata instanceof Number ? ((Number) existingMetadata).intValue() : 0;
            wrapper.setMetadata(TOTAL_TOKEN_USAGE, previousTotal + totalTokenCount);
    	}
        
    	 
    } 
}
