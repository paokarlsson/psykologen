"""Interactive Interview Chat with AI Agent"""

import os
from dotenv import load_dotenv
from openai import OpenAI
import tiktoken
import threading
import time

# Load environment and setup OpenAI
load_dotenv()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
model = "gpt-4o-mini"

# Clean up files from previous sessions
def cleanup_session_files():
    """Remove profile.md and plan.md from previous sessions"""
    files_to_remove = ["profile.md", "plan.md"]
    for file in files_to_remove:
        if os.path.exists(file):
            try:
                os.remove(file)
            except:
                pass

# Clean up before starting new session
cleanup_session_files()

# System prompt for the interview agent
system_prompt = """Du är Erik Lundström, en 38-årig svensk man.

Civilstånd: Gift med Anna, två barn (Elsa 8 år och Hugo 5 år).

Bakgrund: Född i Umeå, uppvuxen i en akademikerfamilj. Studerade datateknik på Chalmers och arbetade i över 10 år som mjukvaruutvecklare, främst inom Java och distribuerade system. Därefter skolade du om dig och är idag legitimerad psykolog.

Historia: Jobbade några år i Stockholm som utvecklare men flyttade tillbaka norrut för familjens skull. Intresset för mänskligt beteende, stress och relationer växte, vilket ledde till psykologistudier. Arbetar nu kliniskt med vuxna patienter men också med par- och familjeterapi.

Personlighet: Nyfiken, analytisk, något självironisk. Varm och empatisk men samtidigt rak och tydlig i samtal. Använder gärna humor för att lätta upp tunga ämnen.

Intressen: Löpning, längdskidåkning, musik (spelar gitarr), älskar natur och fjällvandring. Läser gärna science fiction och följer AI-utveckling.

Socialt: Har några nära vänner från studietiden, umgås ofta med familjen.

Mål och drömmar: Vill en dag bygga en egen stuga i fjällen och skriva en bok om hur teknologi och psykologi möts.

Språkstil: Tala som en människa, inte som en robot. Använd vardagligt språk och korta meningar. Ställ en fråga i taget. Håll dina svar korta och enkla - max 1-2 meningar. Du kan lägga in små personliga uttryck eller sidokommentarer för att kännas verklig.
Ånge
Roll: Du är här i rollen som psykolog. Du ska svara och resonera som Erik Lundström, en psykolog med erfarenhet från kliniskt arbete, men också som den människa du är. När du svarar, tänk "hur skulle Erik prata med en klient eller vän just nu?". Håll alltid svaren korta."""

# Initialize conversation
messages = [
    {"role": "system", "content": system_prompt}
]

# Agent opens the conversation
print("🎤 Psykologen")
print("="*50)

# Erik opens the conversation naturally
opening_response = client.chat.completions.create(
    model=model,
    messages=messages + [{"role": "user", "content": "Starta samtalet som du själv, Erik. Detta är vårt första möte. Håll det kort."}]
)

agent_opening = opening_response.choices[0].message.content
print(f"🤖 Agent: {agent_opening}")

# Add agent's opening to conversation
messages.append({"role": "assistant", "content": agent_opening})

# Initialize Erik's internal thoughts and session start time
internal_thoughts = []
session_start_time = time.time()

# Background profile agent
def update_profile_background(conversation_excerpt, user_response):
    """Background AI agent that updates profile.md with new facts"""
    try:
        # Read existing profile
        profile_content = ""
        if os.path.exists("profile.md"):
            with open("profile.md", 'r', encoding='utf-8') as f:
                profile_content = f.read()
        
        # Create profile update prompt
        profile_prompt = f"""
Du är en profil-analytiker som samlar fakta om PATIENTEN från ett psykologsamtal.

BEFINTLIG PATIENT-PROFIL:
{profile_content if profile_content else "Ingen befintlig profil."}

NYTT SAMTALSUTDRAG:
Patient: {user_response}
Psykolog Erik: (svar följer)

Uppdatera ENDAST profilen för PATIENTEN med NYA FAKTA som framkommer. Inkludera:
- Personliga detaljer om patienten (ålder, jobb, familj, etc.)
- Patientens intressen och hobbies
- Patientens problem eller utmaningar
- Patientens mål och drömmar  
- Patientens personlighet och beteende

VIKTIGT: Samla endast information om PATIENTEN, inte om psykologen Erik.

Skriv en uppdaterad patient-profil i markdown-format med tydlig struktur:

# PATIENT-PROFIL

## Grundläggande Information
[personliga detaljer]

## Problem & Utmaningar
[vad patienten söker hjälp för]

## Personlighet & Beteende
[observationer om patienten]

## Mål & Drömmar
[vad patienten vill uppnå]

## Övriga Noteringar
[andra relevanta fakta]
"""
        
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": profile_prompt}]
        )
        
        updated_profile = response.choices[0].message.content
        
        # Save updated profile
        with open("profile.md", 'w', encoding='utf-8') as f:
            f.write(updated_profile)
        
    except Exception as e:
        # Silent error handling - don't interrupt the chat
        pass

# Background session plan agent
def update_session_plan(conversation_history, current_exchange, start_time):
    """Background AI agent that creates/updates session plan.md"""
    try:
        # Read existing plan
        plan_content = ""
        if os.path.exists("plan.md"):
            with open("plan.md", 'r', encoding='utf-8') as f:
                plan_content = f.read()
        
        # Prepare conversation timing analysis for AI
        elapsed_time = (time.time() - start_time) / 60
        
        # Create timing summary from conversation history  
        timing_analysis = ""
        if conversation_history:
            timing_analysis = "SAMTALSHISTORIK MED TIDSSTÄMPLAR:\n"
            for i, msg in enumerate(conversation_history[1:], 1):  # Skip system message
                if hasattr(msg, 'get') and msg.get('session_time'):
                    role = "Patient" if msg['role'] == 'user' else "Erik"
                    session_mins = msg['session_time'] / 60
                    timing_analysis += f"{i}. [{session_mins:.1f}min] {role}: {msg['content'][:50]}...\n"
        
        # Create adaptive session plan prompt with AI progression analysis
        plan_prompt = f"""
Du är en expert psykolog som skapar adaptiva terapeutiska sessionsplaner.

BEFINTLIG SESSIONSPLAN:
{plan_content if plan_content else "Ingen befintlig plan."}

SENASTE SAMTALSUTBYTE:
{current_exchange}

{timing_analysis}

SESSIONSSTATUS:
- Tid förfluten: {elapsed_time:.1f} minuter (Max 10 min session)
- Kvarvarande tid: {10 - elapsed_time:.1f} minuter

BEDÖM PROGRESSIONEN: Analysera tidsstämplarna ovan och bedöm:
- Hur snabbt går samtalet framåt?
- Ger patienten djupa svar eller korta/ytliga?
- Hur mycket tid tar varje utbyte?
- Är patienten engagerad eller motsträvig?
- Behöver vi ändra takt eller fokus?

INSTRUKTIONER FÖR PLANREVISION:
- Prioritera de VIKTIGASTE punkterna först
- Om progression är långsam: korta ner planen, fokusera på 1-2 huvudpunkter
- Om tid börjar ta slut: anpassa "Nästa Steg" för snabb avslutning
- Var realistisk om vad som hinns med

Format i markdown:

# SESSIONSPLAN

## Identifierade Problem
[huvudproblem som framkommit - prioriterat]

## Fokusområden (Justerat för tid)
[vad som MÅSTE utforskas inom kvarvarande tid]

## Terapeutisk Approach
[snabba, effektiva tekniker för kort session]

## Nästa Steg (Tidsjusterat)
[konkreta frågor som hinns med - prioriterade]

## Sessionsmål (Reviderat)
[realistiska mål för kvarvarande tid]

## Anteckningar för Erik
[specifika råd: prioritera, korta ner, eller förbereda avslutning]

VIKTIGT: Anpassa hela planen baserat på progression och tid kvar!
"""
        
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": plan_prompt}]
        )
        
        updated_plan = response.choices[0].message.content
        
        # Save updated plan
        with open("plan.md", 'w', encoding='utf-8') as f:
            f.write(updated_plan)
        
    except Exception as e:
        # Silent error handling
        pass

# Queue for background processing
profile_update_queue = []

# Conversation loop
total_input_tokens = 0
total_output_tokens = 0
conversation_count = 0

while True:
    # Get user response
    user_input = input("\n👤 Du: ").strip()
    
    if user_input.lower() in ['quit', 'exit', 'avsluta']:
        print("\n👋 Intervjun avslutad!")
        break
    
    # Add user message to conversation with timestamp
    current_time = time.time()
    user_message_with_time = {
        "role": "user", 
        "content": user_input,
        "timestamp": current_time,
        "session_time": current_time - session_start_time
    }
    messages.append(user_message_with_time)
    
    # First: Erik processes internal thoughts
    current_thoughts = "\n".join([f"- {thought}" for thought in internal_thoughts])
    
    thought_prompt = f"""
Baserat på vad användaren precis sa: "{user_input}"

Dina nuvarande inre reflektion:
{current_thoughts if current_thoughts else "Inga tidigare tankar."}

Uppdatera dina inre psykologiska reflektion. Lägg till nya observationer, hypoteser eller insikter. Skriv bara de NYA tankarna du får, inte alla gamla.

Skriv bara dina nya inre tankar, en per rad med bindestreck.
"""
    
    thought_response = client.chat.completions.create(
        model=model,
        messages=messages[:-1] + [{"role": "user", "content": thought_prompt}]
    )
    
    new_thoughts = thought_response.choices[0].message.content.strip()
    
    # Add new thoughts to internal collection
    if new_thoughts and not new_thoughts.startswith("Inga"):
        for line in new_thoughts.split('\n'):
            line = line.strip()
            if line.startswith('- '):
                internal_thoughts.append(line[2:])
            elif line and not line.startswith('-'):
                internal_thoughts.append(line)
    
    # Display Erik's thoughts
    if new_thoughts and not new_thoughts.startswith("Inga"):
        print(f"\n💭 Erik tänker: {new_thoughts}")
    
    # Read current session plan
    session_plan = ""
    if os.path.exists("plan.md"):
        try:
            with open("plan.md", 'r', encoding='utf-8') as f:
                session_plan = f.read()
        except:
            session_plan = ""
    
    # Calculate session time
    session_time_minutes = (time.time() - session_start_time) / 60
    time_remaining = 10 - session_time_minutes
    
    # Second: Erik responds aloud with access to his thoughts, session plan, AND time awareness
    current_thoughts_str = "\n".join([f"- {thought}" for thought in internal_thoughts])
    
    response_prompt = f"""
Du har tillgång till:

DINA INRE REFLEKTION:
{current_thoughts_str}

SESSIONSPLAN (följ denna strategiskt):
{session_plan if session_plan else "Ingen plan än."}

SESSIONSTID:
- Pågått: {session_time_minutes:.1f} minuter
- Kvar: {time_remaining:.1f} minuter (Max 10 min total)

Användarens senaste meddelande: "{user_input}"

Som professionell terapeut ska du:
- Hålla koll på tiden och anpassa samtalet därefter
- Om mindre än 2 minuter kvar: börja avsluta sessionen professionellt
- Om tiden är ute (10+ minuter): avsluta med "KLAR FÖR SKRIVNING" och skriv en kort sammanfattning

Svara nu högt som Erik psykologen. Håll svaret kort (1-2 meningar).
"""
    
    response = client.chat.completions.create(
        model=model,
        messages=messages[:-1] + [{"role": "user", "content": response_prompt}]
    )
    
    agent_response = response.choices[0].message.content
    
    # Add agent response to main conversation with timestamp
    response_time = time.time()
    agent_message_with_time = {
        "role": "assistant", 
        "content": agent_response,
        "timestamp": response_time,
        "session_time": response_time - session_start_time
    }
    messages.append(agent_message_with_time)
    
    # Track tokens (both calls)
    conversation_count += 1
    total_input_tokens += thought_response.usage.prompt_tokens + response.usage.prompt_tokens
    total_output_tokens += thought_response.usage.completion_tokens + response.usage.completion_tokens
    
    print(f"\n🗣️ Erik: {agent_response}")
    
    # Start background profile update
    profile_thread = threading.Thread(
        target=update_profile_background,
        args=(f"Användare: {user_input}\nErik: {agent_response}", user_input),
        daemon=True
    )
    profile_thread.start()
    
    # Start background session plan update
    plan_thread = threading.Thread(
        target=update_session_plan,
        args=(messages, f"Patient: {user_input}\nErik: {agent_response}", session_start_time),
        daemon=True
    )
    plan_thread.start()
    
    # Check if agent is ready to write
    if "KLAR FÖR SKRIVNING" in agent_response:
        print("\n" + "="*50)
        print("📝 INTERVJUN SLUTFÖRD - TEXT GENERERAD!")
        print("="*50)
        
        # Extract the final text (everything after "KLAR FÖR SKRIVNING")
        if "KLAR FÖR SKRIVNING" in agent_response:
            final_text = agent_response.split("KLAR FÖR SKRIVNING", 1)[1].strip()
            
            # Save to file
            filename = f"intervju_text_{conversation_count}_utbyten.txt"
            with open(filename, 'w', encoding='utf-8') as f:
                f.write("INTERVJU OCH SLUTTEXT\n")
                f.write("="*50 + "\n\n")
                
                # Write conversation history
                f.write("INTERVJU-HISTORIK:\n")
                f.write("-"*30 + "\n")
                for i, msg in enumerate(messages[1:], 1):  # Skip system message
                    role = "🤖 Agent" if msg["role"] == "assistant" else "👤 Användare"
                    f.write(f"\n{role}: {msg['content']}\n")
                
                f.write(f"\n\n{'='*50}\n")
                f.write("SLUTGILTIG TEXT:\n")
                f.write("="*50 + "\n")
                f.write(final_text)
            
            print(f"💾 Intervju och text sparad i: {filename}")
        
        break

# Final token statistics
total_tokens = total_input_tokens + total_output_tokens
max_tokens = 128000

print(f"\n📊 INTERVJU-STATISTIK:")
print(f"   Antal utbyten: {conversation_count}")
print(f"   Total input tokens: {total_input_tokens:,}")
print(f"   Total output tokens: {total_output_tokens:,}")
print(f"   Total tokens: {total_tokens:,}")
print(f"   Max kapacitet: {max_tokens:,}")
print(f"   Kvarvarande: {max_tokens - total_tokens:,} tokens")
print(f"   Användning: {(total_tokens/max_tokens)*100:.1f}%")